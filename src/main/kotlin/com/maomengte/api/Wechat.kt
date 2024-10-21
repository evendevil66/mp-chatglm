package com.maomengte.api

import com.example.config.AppConfig
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.maomengte.model.WeChatMessage
import com.zhipu.oapi.ClientV4
import com.zhipu.oapi.Constants
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest
import com.zhipu.oapi.service.v4.model.ChatMessage
import com.zhipu.oapi.service.v4.model.ChatMessageRole
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


val logger: Logger = LoggerFactory.getLogger("WechatModule")
data class PendingRequest(
    var deferred: CompletableDeferred<String>,
    var retryCount: Int = 0
)

val pendingRequests = ConcurrentHashMap<Long, PendingRequest>() // 存储正在处理的请求

// 用于存储用户消息和响应的类
data class UserMessageCache(
    val messages: MutableList<ChatMessage>, // 存储请求和响应的列表
    var timestamp: Long
)

// 存储缓存的ConcurrentHashMap，key为用户的 fromUserName
val userMessageCache = ConcurrentHashMap<String, UserMessageCache>()

fun Route.wechat() {


    get("/wechat") {
        val signature = call.parameters["signature"]
        val timestamp = call.parameters["timestamp"]
        val nonce = call.parameters["nonce"]
        val echostr = call.parameters["echostr"]

        if (checkSignature(signature, timestamp, nonce)) {
            // 校验成功，返回 echostr
            call.respondText(echostr ?: "", ContentType.Text.Plain)
        } else {
            // 校验失败，返回错误信息
            call.respond(HttpStatusCode.Forbidden, "Invalid signature")
        }
    }

    post("/wechat") {
        logger.info("进入微信Post消息")
        // 接收微信推送的XML消息
        val body = call.receiveText()
        // 解析XML数据
        val xmlMapper = XmlMapper()
        val xml = xmlMapper.readValue(body, WeChatMessage::class.java)
        val toUserName = xml.toUserName
        val fromUserName = xml.fromUserName
        val msgType = xml.msgType
        val content = xml.content
        val msgId = xml.msgId
        var responseMessage = "success"
        when (msgType) {
            "event" -> {
                handleEventMessage(xml, fromUserName, toUserName)
            }
            "text" -> {
                handleTextMessage(content, fromUserName, toUserName, responseMessage, msgId)
            }
            else -> {
                responseMessage = "暂不支持此消息类型，我会努力升级的！"
                call.respondText(replyTextMessage(fromUserName, toUserName,responseMessage), ContentType.Text.Xml)
                return@post
            }
        }
    }

}

private suspend fun RoutingContext.handleTextMessage(
    content: String?,
    fromUserName: String,
    toUserName: String,
    responseMessage: String,
    msgId: Long
) {
    var responseMessage1 = responseMessage
    logger.info("进入微信Text消息")
    if (content.isNullOrEmpty()) {
        call.respondText(replyTextMessage(fromUserName, toUserName, responseMessage1), ContentType.Text.Xml)
        return
    }
    if (content.trim() == "获取结果") {
        val userMessageCache = userMessageCache[fromUserName]
        val lastMessage = userMessageCache?.messages?.lastOrNull()
        if (lastMessage == null || lastMessage.role == "user") {
            val errorResponse = "你还没有向我提问过，或者我还没有生成回答，请先向我提问或稍后再试哦！"
            call.respondText(replyTextMessage(fromUserName, toUserName, errorResponse), ContentType.Text.Xml)
            return
        }
        if (lastMessage.role == "assistant") {
            responseMessage1 = lastMessage.content.toString()
            call.respondText(replyTextMessage(fromUserName, toUserName, responseMessage1), ContentType.Text.Xml)
            return
        }
    }
    if (content.trim() == "清空记忆") {
            userMessageCache.remove(fromUserName)
            responseMessage1 = "记忆已清空，你可以向我提问问题啦！"
            call.respondText(replyTextMessage(fromUserName, toUserName, responseMessage1), ContentType.Text.Xml)

    }
    // 检查是否已有相同 msgId 的请求在处理
    val pendingRequest = pendingRequests[msgId]

    if (pendingRequest != null) {
        if (pendingRequest.deferred.isCompleted) {
            logger.info("请求已完成，返回结果")
            // 请求已完成，返回结果
            val result = pendingRequest.deferred.await()
            call.respondText(replyTextMessage(fromUserName, toUserName, result), ContentType.Text.Xml)
            return
        }
        logger.info("已有相同 msgId ： $msgId 的请求正在处理，增加重试次数，当前重试次数为：${pendingRequest.retryCount}")
        pendingRequest.retryCount++
        val result = awaitResult(pendingRequest, fromUserName, toUserName)
        if (result == null) {
            logger.info("本次请求未能获得结果")
            if (pendingRequest.retryCount >= 2) {
                val errorResponse = "您的消息较长，我正在处理啦，请你稍等一下下给我发送“获取结果”，我将发送结果给你哦~"
                call.respondText(replyTextMessage(fromUserName, toUserName, errorResponse), ContentType.Text.Xml)
                pendingRequests.remove(msgId)
                return
            }
        }

    } else {
        // 开始异步处理请求
        val deferred = CompletableDeferred<String>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = chatGlm(fromUserName, content)
                deferred.complete(result)
            } catch (e: Exception) {
                logger.error("处理异步请求时出错，msgId: $msgId", e)
                val errorResponse = "处理消息时发生错误，请减少请求内容，或稍后重试！"
                call.respondText(replyTextMessage(fromUserName, toUserName, errorResponse), ContentType.Text.Xml)
                //删除缓存中的最后一个数据
                val userMessageCache = userMessageCache[fromUserName]
                userMessageCache?.messages?.removeLast()
            }
        }

        pendingRequests[msgId] = PendingRequest(deferred = deferred)
    }
}

private suspend fun RoutingContext.awaitResult(pendingRequest: PendingRequest, fromUserName: String, toUserName: String): String? {
    val timeoutAttempts: List<Long> = listOf(1000, 2000, 1500) // 定义超时时间列表
    for (timeout in timeoutAttempts) {
        val result = withTimeoutOrNull(timeout) {
            pendingRequest.deferred.await()
        }
        if (result != null) {
            logger.info("异步请求已完成，返回结果")
            logger.info("返回结果：$result")
            call.respondText(replyTextMessage(fromUserName, toUserName, result), ContentType.Text.Xml)
            return result
        }
    }
    return null // 如果没有获得结果，则返回 null
}

private suspend fun RoutingContext.handleEventMessage(
    xml: WeChatMessage,
    fromUserName: String,
    toUserName: String
) {
    val event = xml.event
    if (event == "subscribe") {
        call.respondText(replyTextMessage(fromUserName, toUserName, AppConfig.followMessage), ContentType.Text.Xml)
        return
    }
}

fun getTotalSize(messages: List<ChatMessage>): Int {
    return messages.sumOf { it.toString().toByteArray().size }
}

//异步请求chatGlm
fun chatGlm(fromUserName:String,content:String):String{
    logger.info("进入chatGlm请求，fromUserName: $fromUserName")
    val currentTime = System.currentTimeMillis()
    val cachedData = userMessageCache[fromUserName]

    // 清理过期的缓存
    cachedData?.let {
        if (currentTime - it.timestamp >= TimeUnit.HOURS.toMillis(12)) {
            userMessageCache.remove(fromUserName) // 移除过期的缓存
        }
    }
    // 初始化 messages 列表
    val messages: MutableList<ChatMessage> = mutableListOf()




    // 如果缓存存在，添加历史消息
    cachedData?.messages?.let { previousMessages ->
        messages.addAll(previousMessages)
    }
    if(messages.isEmpty()){
        messages.add(ChatMessage(ChatMessageRole.USER.value(),AppConfig.preset))
    }

    // 检查新消息大小是否超过128K
    if (content.toByteArray().size > 128 * 1024) {
        return "消息过大，请精简后重试！"
    }
    messages.add(ChatMessage(ChatMessageRole.USER.value(), content))

    // 如果总大小超过128K，移除最早的消息
    while (getTotalSize(messages) > 128 * 1024) { // 128K
        messages.removeAt(1) // 移除最早的消息
    }

    // 更新缓存
    userMessageCache[fromUserName] = UserMessageCache(messages, currentTime)

    //遍历 messages，输出日志
    for (message in messages) {
        logger.info("Role: ${message.role}, Content: ${message.content}")
    }
    logger.info("对话记录输出完成")

    val client = ClientV4.Builder(AppConfig.chatGlmApiKey)
        .networkConfig(30, 30, 30, 30, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(8, 1, TimeUnit.SECONDS))
        .build()
    val chatCompletionRequest = ChatCompletionRequest.builder()
        .model(AppConfig.model)
        .stream(java.lang.Boolean.FALSE)
        .invokeMethod(Constants.invokeMethod)
        .messages(messages)
        .build()
    val invokeModelApiResp = client.invokeModelApi(chatCompletionRequest)
    var responseMessage = invokeModelApiResp.data.choices[0].message.content.toString()

    // 添加助手的响应到消息列表
    messages.add(ChatMessage(ChatMessageRole.ASSISTANT.value(), responseMessage))
    if(userMessageCache.size>6 && userMessageCache.size%4==0){
        responseMessage=
            "$responseMessage\n如果前面的对话对你来说没有用了，可以发送“清空记忆”给我，让我忘掉前面的记忆，响应速度更快哦～"
    }
    // 更新缓存
    userMessageCache[fromUserName] = UserMessageCache(messages, currentTime)
    logger.info("请求结果获取成功：$responseMessage")

    return responseMessage
}

//回复文本消息
fun replyTextMessage(fromUserName:String, toUserName: String,responseMessage: String):String {
    // 构造返回的XML消息
    val sanitizedMessage = responseMessage
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .trim() // 去除多余空格和换行

    return """
        <xml>
          <ToUserName><![CDATA[$fromUserName]]></ToUserName>
          <FromUserName><![CDATA[$toUserName]]></FromUserName>
          <CreateTime>${System.currentTimeMillis() / 1000}</CreateTime>
          <MsgType><![CDATA[text]]></MsgType>
          <Content><![CDATA[$sanitizedMessage]]></Content>
        </xml>
    """.trimIndent()

}

// 校验 signature 的函数
private fun checkSignature(signature: String?, timestamp: String?, nonce: String?): Boolean {
    val token = AppConfig.token // 从配置中读取 Token

    // 将 token、timestamp 和 nonce 按字典序排序
    val params = listOfNotNull(token, timestamp, nonce).sorted()
    // 拼接成字符串
    val tmpStr = params.joinToString("")
    // 进行 SHA1 加密
    val sha1Hash = MessageDigest.getInstance("SHA-1").digest(tmpStr.toByteArray()).joinToString("") {
        "%02x".format(it)
    }

    // 返回校验结果
    return sha1Hash == signature
}


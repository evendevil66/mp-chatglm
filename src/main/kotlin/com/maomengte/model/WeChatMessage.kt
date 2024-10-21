package com.maomengte.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeChatMessage(
    @JacksonXmlProperty(localName = "ToUserName")
    val toUserName: String,

    @JacksonXmlProperty(localName = "FromUserName")
    val fromUserName: String,

    @JacksonXmlProperty(localName = "CreateTime")
    val createTime: Long,

    @JacksonXmlProperty(localName = "MsgType")
    val msgType: String,

    @JacksonXmlProperty(localName = "Content")
    val content: String? = null,

    @JacksonXmlProperty(localName = "MsgId")
    val msgId: Long,

    // 针对不同 MsgType 的字段
    @JacksonXmlProperty(localName = "PicUrl")
    val picUrl: String? = null, // 针对 image 类型

    @JacksonXmlProperty(localName = "MediaId")
    val mediaId: String? = null, // 针对 image、voice、video 类型

    @JacksonXmlProperty(localName = "Format")
    val format: String? = null, // 针对 voice 类型

    @JacksonXmlProperty(localName = "ThumbMediaId")
    val thumbMediaId: String? = null, // 针对 video 类型

    @JacksonXmlProperty(localName = "Event")
    val event: String? = null // 处理事件类型
)

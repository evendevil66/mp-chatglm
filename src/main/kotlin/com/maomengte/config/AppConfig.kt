package com.maomengte.config
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*

object AppConfig {
    private val config = HoconApplicationConfig(ConfigFactory.load())
    // 读取 ak 和 as
    val appid: String by lazy { config.property("app.appid").getString() }
    val appSecret: String by lazy { config.property("app.appSecret").getString() }
    val token = config.property("app.token").getString()
    val encodingAESKey = config.property("app.encodingAESKey").getString()
    val followMessage: String by lazy { config.property("app.followMessage").getString() }
    val chatGlmApiKey: String by lazy { config.property("app.chatGlmApiKey").getString() }
    val model = config.property("app.chatGlmModel").getString()
    val preset = config.property("app.preset").getString()
}

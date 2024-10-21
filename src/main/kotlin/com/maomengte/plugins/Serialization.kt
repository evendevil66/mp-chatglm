package com.maomengte.plugins

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson(ContentType.Application.Xml) {
            // 直接使用 XmlMapper 并注册 Kotlin 模块
            XmlMapper().apply {
                registerKotlinModule()
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }
    }
    routing {
        get("/json/jackson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}

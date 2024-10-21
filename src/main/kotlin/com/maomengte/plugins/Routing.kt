package com.maomengte.plugins

import com.maomengte.api.wechat
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        wechat()
        get("/") {
            call.respondText("Hello World!")
        }
    }
}

# 微信公众号接入智谱AI

## 项目简介

本项目旨在通过微信公众号接口接入智谱AI，提供智能对话和自动回复功能，提升用户交互体验。  
项目比较菜，其实没什么可学习的点，功能也较少，主要是为了提供给未认证的公众号进行使用，如果觉得好的话请点一个Star。  

## **体验公众号：小赫小朋友**

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [使用方法](#使用方法)
- [自行编译](#自行编译)
- [使用说明](#使用说明)

## 功能特性

- 用户消息自动回复  
  未知原因微信接口不回复只会重试一次，所以当10秒内无回复，则自动回复生成中，并要求用户稍后回复“获取结果”拿到数据，短内容可即时响应。
- 智能对话接口接入   
  V4-2.3.0 SDK 支持多轮对话，记忆存储，默认使用12小时内的记忆，总内容超出128K自动删除最早的记忆内容。
- 适配未认证公众号、个人号
  不使用高级接口，仅支持文字回复，利用“获取结果”命令，可在无客服回复接口下也能实现长文本回答。

## 技术栈

- Kotlin
- Ktor
- 微信公众号 API
- 智谱AI SDK

## 使用方法
1、**通过Actions或Releases页面下载编译好的jar包**  
2、**创建application.conf文件，并填写配置信息。其中appid、appSecret、encodingAESKey暂时非必要可留空**


```bash
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.maomengte.ApplicationKt.module ]
    }
}

app {
    appid = "***"  #公众号appid
    appSecret = "***"  #公众号appSecret
    token = "***"  #公众号token
    encodingAESKey = "***"  #公众号encodingAESKey
    followMessage = "欢迎关注小赫小朋友，快来一起玩吧！"  #关注公众号回复
    chatGlmApiKey = "***"  #智谱apikey
    chatGlmModel = "glm-4-plus"  #模型名称
    #人设
    preset = "请注意，你是一个智能助手，名字叫小赫，现在为用户提供服务，你会解答用户的各种问题，请称呼用户为“宝贝”。"
}
```
3、**运行jar文件并自行进行反向代理映射**
```bash
java -jar jar文件.jar -Dconfig.file=配置文件路径/application.conf
```


## 自行编译

1. **克隆项目：**
   ```bash
   git clone https://github.com/你的用户名/项目名称.git
   cd 项目名称
   ```
2. **配置src/main/resources/application.conf文件**
3. **使用 Gradle 构建项目：**
    ```bash
    ./gradlew build
    ```
4. **运行项目：**
    ```
    java -jar build/libs/项目名称-版本号.jar
    ```

## 使用说明

1. **请先成功运行项目，并自己通过反向代理配置域名**
2. **在微信公众号后台配置域名和token等信息，域名为“https://your_domain.com/wechat”**
3. **微信配置通过后，即可通过微信公众号发送消息进行对话**

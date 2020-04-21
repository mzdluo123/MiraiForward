package org.helloworld.mirai.forward

import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugins.PluginBase
import net.mamoe.mirai.console.plugins.withDefaultWriteSave
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.uploadAsImage
import java.net.URL

internal object Forward : PluginBase() {
    private val config = loadConfig("setting.yml")
    val groups by lazy {
        config.setIfAbsent("groups", mutableListOf<Long>())
        config.getLongList("groups").toMutableList()
    }
    val disableList by lazy {
        config.setIfAbsent("disableList", mutableListOf<Long>())
        config.getLongList("disableList").toMutableList()
    }
    val lockList by lazy {
        config.setIfAbsent("lockList", mutableListOf<Long>())
        config.getLongList("lockList").toMutableList()
    }

    var status by config.withDefaultWriteSave { false }
    var avatarShow by config.withDefaultWriteSave { true }
    var raw by config.withDefaultWriteSave { true }
     val avatarCache = mutableMapOf<Long, Image>()

    fun saveAll() {
        config["groups"] = groups
        config["disableList"] = disableList
        config["lockList"] = lockList
        config.save()
        ForwardInfo.saveAll()
    }

    override fun onLoad() {
        registerForwardCommand()
        ForwardInfo.init()
    }

    override fun onEnable() {
        super.onEnable()
        logger.info("转发插件加载")
        subscribeGroupMessages {
            always {
                if (!status) {
                    return@always
                }

                if (group.id !in groups) {
                    return@always
                }

                if (message.contentToString() == "停止转发") {
                    if (sender.id !in disableList && sender.id !in lockList) {
                        reply(At(sender) + "成功暂停你的消息转发")
                        disableList.add(sender.id)
                        return@always
                    }
                }
                if (message.contentToString() == "启动转发") {
                    if (sender.id in disableList && sender.id !in lockList) {
                        reply(At(sender) + "成功开启你的消息转发")
                        disableList.remove(sender.id)
                        return@always
                    }
                }

                if (sender.id in disableList) {
                    return@always
                }

                val messageChainBuilder = MessageChainBuilder()
                if (raw &&
                    message.contentToString().length > 4 &&
                    message.contentToString().substring(0, 4) == "#raw"
                ) {
                    message.foreachContent {
                        if (it is PlainText) {
                            messageChainBuilder.add(it.replaceFirst("#raw".toRegex(), ""))
                            return@foreachContent
                        }
                        messageChainBuilder.add(it)
                    }
                    send(group, messageChainBuilder.asMessageChain(), bot)
                    return@always
                }
                var isQuote = false
                if (avatarShow) {
                    messageChainBuilder.add(0, getAvatar(sender.id, group))
                }
                messageChainBuilder.add("[${sender.nameCardOrNick}]\n".toMessage())
                for (i in message) {
                    if (i is QuoteReply) {
                        isQuote = true
                        break
                    }
                    if ("/forward" in i.contentToString()) {
                        return@always
                    }
                }
                for (i in message) {
                    if (isQuote && i is At) {
                        continue
                    }
                    messageChainBuilder.add(i)
                }
                send(group, messageChainBuilder.asMessageChain(), bot)
            }
        }
    }

    private suspend inline fun send(group: Group, messageChain: MessageChain, bot: Bot) {
        for (i in groups.indices) {
            val g = groups[i]
            if (g == group.id) {
                ForwardInfo.addSend(i)
                continue
            }
            bot.getGroup(g).sendMessage(messageChain)
        }
//        groups.filter { it != group.id }
//            .forEach {
//                bot.getGroup(it)
//                    .sendMessage(messageChain)
//            }
    }

    private suspend inline fun getAvatar(id: Long, contact: Contact): Image {
        var img = avatarCache[id]
        if (img != null) {
            return img
        }
        img = URL("http://q1.qlogo.cn/g?b=qq&nk=${id}&s=1").uploadAsImage(contact)
        avatarCache[id] = img
        return img
    }

    override fun onDisable() {
        super.onDisable()
        saveAll()
    }
}
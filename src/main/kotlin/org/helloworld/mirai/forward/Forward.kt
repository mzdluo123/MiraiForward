package org.helloworld.mirai.forward

import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.plugins.PluginBase
import net.mamoe.mirai.console.plugins.withDefaultWriteSave
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.MemberJoinEvent
import net.mamoe.mirai.event.events.MemberLeaveEvent
import net.mamoe.mirai.event.events.MemberMuteEvent
import net.mamoe.mirai.event.events.MemberUnmuteEvent
import net.mamoe.mirai.event.subscribeAlways
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
    var showSysMsg by config.withDefaultWriteSave { true }

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
        setupMsgHandle()
        setupSysMsgHandle()
    }

    private suspend inline fun send(group: Group, messageChain: MessageChain, bot: Bot) {
        for (i in groups.indices) {
            val g = groups[i]
            if (g == group.id) {
                ForwardInfo.addSend(i)
                continue
            }
            launch { bot.getGroup(g).sendMessage(messageChain) }
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

    private fun setupMsgHandle() {
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
                    message.forEachContent {
                        if (it is PlainText) {
                            messageChainBuilder.add(it.content.replaceFirst("#raw".toRegex(), ""))
                            return@forEachContent
                        }
                        messageChainBuilder.add(it)
                    }
                    send(group, messageChainBuilder.asMessageChain(), bot)
                    return@always
                }
                var isQuote = false  //是否是quote消息
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
                    if (i is QuoteReply){

                    }
                    messageChainBuilder.add(i)
                }

                send(group, messageChainBuilder.asMessageChain(), bot)
            }
        }

    }


    private fun setupSysMsgHandle() {
        subscribeAlways<MemberJoinEvent> {
            if (group.id in groups && showSysMsg) {
                send(group, "${member.nameCardOrNick}加入了${group.name}".toMessage().asMessageChain(), bot)
            }
        }

        subscribeAlways<MemberLeaveEvent> {
            if (group.id in groups && showSysMsg) {
                send(group, "${member.nameCardOrNick}退出了${group.name}".toMessage().asMessageChain(), bot)
            }
        }
        subscribeAlways<MemberMuteEvent> {
            if (group.id in groups && showSysMsg) {
                send(group, "${member.nameCardOrNick}被禁言了".toMessage().asMessageChain(), bot)
            }
        }
        subscribeAlways<MemberUnmuteEvent> {
            if (group.id in groups && showSysMsg) {
                send(group, "${member.nameCardOrNick}可以说话了".toMessage().asMessageChain(), bot)
            }
        }
    }


    override fun onDisable() {
        super.onDisable()
        logger.error("Forward disable")
        saveAll()
    }
}
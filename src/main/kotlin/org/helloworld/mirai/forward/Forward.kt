package org.helloworld.mirai.forward

import net.mamoe.mirai.console.command.registerCommand
import net.mamoe.mirai.console.plugins.PluginBase
import net.mamoe.mirai.console.plugins.withDefaultWriteSave
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.toMessage
import java.net.URL

object Forward : PluginBase() {
    private val config = loadConfig("setting.yml")
    private val groups by lazy {
        config.setIfAbsent("groups", mutableListOf<Long>())
        config.getLongList("groups").toMutableList()
    }
    private val disableList by lazy {
        config.setIfAbsent("disableList", mutableListOf<Long>())
        config.getLongList("disableList").toMutableList()
    }
    private val lockList by lazy {
        config.setIfAbsent("lockList", mutableListOf<Long>())
        config.getLongList("lockList").toMutableList()
    }

    private var status by config.withDefaultWriteSave { false }
    private var avatarShow by config.withDefaultWriteSave { true }


    private fun saveAll() {
        config["groups"] = groups
        config["disableList"] = disableList
        config["lockList"] = lockList

        config.save()
    }

    override fun onLoad() {
        registerCommand {
            name = "forward"
            description = "设置转发"
            usage = """qq跨群转发插件
使用 停止转发 或 启动转发 可设置是否转发你的消息
/forward
   group <群号> 将当前群加入/移除转发组
   start  开启转发
   stop  关闭转发
   lock <qq> 锁定某成员的转发开关
   forward <qq>  更改成员的转发开关
   avatar 转发时显示头像开关
            """.trimIndent()
            onCommand { args: List<String> ->

                if (args.isEmpty()) {
                    return@onCommand false
                }

                when (args[0]) {
                    "group" -> {
                        if (args.size < 2) {
                            return@onCommand false
                        }
                        val groupId = args[1].toLong()
                        if (groupId in groups) {
                            groups.remove(groupId)
                            sendMessage("成功删除")
                        } else {
                            groups.add(groupId)
                            sendMessage("成功添加")
                        }
                        saveAll()
                        return@onCommand true
                    }
                    "start" -> {
                        status = true
                        sendMessage("成功开启")
                        saveAll()
                        return@onCommand true
                    }
                    "stop" -> {
                        status = false
                        sendMessage("成功关闭")
                        saveAll()
                        return@onCommand true
                    }
                    "lock" -> {
                        if (args.size < 2) {
                            return@onCommand false
                        }
                        val memberId = args[1].toLong()
                        if (memberId in lockList) {
                            lockList.remove(memberId)
                            sendMessage("成功解除锁定")
                            saveAll()
                            return@onCommand true
                        }
                        lockList.add(memberId)
                        sendMessage("成功锁定")
                        saveAll()
                        return@onCommand true
                    }
                    "forward" -> {
                        if (args.size < 2) {
                            return@onCommand false
                        }
                        val memberId = args[1].toLong()
                        if (memberId in disableList) {
                            disableList.remove(memberId)
                            sendMessage("成功开启转发")
                            saveAll()
                            return@onCommand true
                        }
                        disableList.add(memberId)
                        sendMessage("成功关闭转发")
                        saveAll()
                        return@onCommand true
                    }
                    "avatar" -> {
                        if (avatarShow) {
                            avatarShow = false
                            sendMessage("成功关闭")
                            saveAll()
                            return@onCommand true
                        }
                        avatarShow = true
                        sendMessage("成功开启")
                        return@onCommand true
                    }
                }
                return@onCommand false
            }
        }
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
                var isQuote = false
                if (avatarShow) {
                    messageChainBuilder.add(
                        0,
                        URL("http://q1.qlogo.cn/g?b=qq&nk=${sender.id}&s=1").uploadAsImage()
                    )
                }
                messageChainBuilder.add("[${sender.nameCardOrNick}]\n".toMessage())
                for (i in message) {
                    if (i is QuoteReply) {
                        isQuote = true
                        break
                    }
                    if (i.contentToString() == "/forward") {
                        return@always
                    }
                }
                for (i in message) {
                    if (isQuote && i is At) {
                        continue
                    }
                    messageChainBuilder.add(i)
                }


                groups.filter { it != group.id }
                    .forEach {
                        bot.getGroup(it)
                            .sendMessage(messageChainBuilder.asMessageChain())
                    }

            }

        }
    }

    override fun onDisable() {
        super.onDisable()
        saveAll()
    }
}
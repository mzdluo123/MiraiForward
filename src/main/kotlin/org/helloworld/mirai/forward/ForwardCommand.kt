package org.helloworld.mirai.forward

import net.mamoe.mirai.console.command.registerCommand

internal fun registerForwardCommand() {
    Forward.registerCommand {
        name = "forward"
        description = "设置转发"
        usage = """qq跨群转发插件
使用 停止转发 或 启动转发 可设置是否转发你的消息
使用#raw作为信息开头可以不显示发送者信息进行转发
/forward
   group <群号> 将当前群加入/移除转发组
   start  开启转发
   stop  关闭转发
   lock <qq> 锁定某成员的转发开关
   forward <qq>  更改成员的转发开关
   avatar 转发时显示头像开关
   raw  是否允许原消息转发(不显示发送者信息)
   info 显示统计信息
   clean  清除统计信息
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
                    if (groupId in Forward.groups) {
                        Forward.groups.remove(groupId)
                        ForwardInfo.delGroup(Forward.groups.indexOf(groupId))
                        sendMessage("成功删除")
                    } else {
                        Forward.groups.add(groupId)
                        ForwardInfo.init()
                        sendMessage("成功添加")
                    }
                    Forward.saveAll()
                    return@onCommand true
                }
                "start" -> {
                    Forward.status = true
                    sendMessage("成功开启")
                    Forward.saveAll()
                    return@onCommand true
                }
                "stop" -> {
                    Forward.status = false
                    sendMessage("成功关闭")
                    Forward.saveAll()
                    return@onCommand true
                }
                "lock" -> {
                    if (args.size < 2) {
                        return@onCommand false
                    }
                    val memberId = args[1].toLong()
                    if (memberId in Forward.lockList) {
                        Forward.lockList.remove(memberId)
                        sendMessage("成功解除锁定")
                        Forward.saveAll()
                        return@onCommand true
                    }
                    Forward.lockList.add(memberId)
                    sendMessage("成功锁定")
                    Forward.saveAll()
                    return@onCommand true
                }
                "forward" -> {
                    if (args.size < 2) {
                        return@onCommand false
                    }
                    val memberId = args[1].toLong()
                    if (memberId in Forward.disableList) {
                        Forward.disableList.remove(memberId)
                        sendMessage("成功开启转发")
                        Forward.saveAll()
                        return@onCommand true
                    }
                    Forward.disableList.add(memberId)
                    sendMessage("成功关闭转发")
                    Forward.saveAll()
                    return@onCommand true
                }
                "avatar" -> {
                    if (Forward.avatarShow) {
                        Forward.avatarShow = false
                        sendMessage("成功关闭")
                        Forward.saveAll()
                        return@onCommand true
                    }
                    Forward.avatarShow = true
                    sendMessage("成功开启")
                    return@onCommand true
                }
                "raw" -> {
                    if (Forward.raw) {
                        Forward.raw = false
                        sendMessage("成功关闭")
                        Forward.saveAll()
                        return@onCommand true
                    }
                    Forward.raw = true
                    sendMessage("成功开启")
                    return@onCommand true
                }
                "info" -> {
                    sendMessage(ForwardInfo.info)
                    return@onCommand true
                }

                "clean" -> {
                    ForwardInfo.clean()
                    sendMessage("重置成功")
                    return@onCommand true
                }

            }
            return@onCommand false
        }
    }

}
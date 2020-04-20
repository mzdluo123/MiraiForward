package org.helloworld.mirai.forward

import net.mamoe.mirai.console.plugins.withDefaultWriteSave
import java.lang.StringBuilder
import java.text.SimpleDateFormat

object ForwardInfo {
    private val infoConfig = Forward.loadConfig("infoConfig.yml")
    var startTime by infoConfig.withDefaultWriteSave { System.currentTimeMillis() }

    private val sendList by lazy {
        infoConfig.setIfAbsent("sendList", mutableListOf<Long>())
        infoConfig.getLongList("sendList").toMutableList()
    }

    fun init() {
        val groups = Forward.groups.size
        while (sendList.size < groups) {
            sendList.add(0)
        }

    }

    fun addSend(index: Int) {
        sendList[index]++
    }

    fun delGroup(index: Int){
        sendList[index] = 0
    }
    fun clean() {
        sendList.clear()
        init()
        startTime = System.currentTimeMillis()
    }

    fun saveAll() {
        infoConfig["sendList"] = sendList
        infoConfig.save()
    }

    val info: String
        get() = run {
            val sb = StringBuilder()
            sb.append("从${SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss").format(startTime)}开始的统计数据:\n")

            for (i in Forward.groups.indices) {
                sb.append("群${Forward.groups[i]}发出${sendList[i]}条转发消息\n")
            }
            sb.toString()
        }
}
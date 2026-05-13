package com.komica.reader.data

import com.komica.reader.model.Board
import com.komica.reader.model.BoardCategory
import com.komica.reader.model.KomicaThread

object SampleData {
    val BoardCategories = listOf(
        BoardCategory(
            name = "常用看板",
            boards = listOf(
                Board("綜合", "http://komica1.org/00/index.htm", "一般討論與日常串流", "常用看板"),
                Board("新番捏他", "http://komica1.org/15/index.htm", "動畫新番與劇情討論", "常用看板")
            )
        ),
        BoardCategory(
            name = "分類",
            boards = listOf(
                Board("遊戲", "http://komica1.org/game/index.htm", "家機、PC、手機遊戲", "分類"),
                Board("創作", "http://komica1.org/paint/index.htm", "繪圖、模型、攝影", "分類"),
                Board("生活", "http://komica1.org/life/index.htm", "飲食、旅遊、閒聊", "分類")
            )
        )
    )

    fun Threads(board: Board): List<KomicaThread> {
        return listOf(
            KomicaThread("1", "今天晚餐要吃什麼", "Anonymous", 42, board.url, 1001, contentPreview = "附近新開的店看起來不錯，有沒有人踩過點...", lastReplyTime = "5 分鐘前"),
            KomicaThread("2", "週末電影討論串", "Anonymous", 18, board.url, 1002, contentPreview = "這週院線片不少，想找輕鬆一點的片。", lastReplyTime = "12 分鐘前"),
            KomicaThread("3", "桌面整理分享", "Anonymous", 73, board.url, 1003, imageUrl = "sample", contentPreview = "換了新佈景，順便整理了一下常用工具。", lastReplyTime = "21 分鐘前"),
            KomicaThread("4", "問題集中串", "Anonymous", 9, board.url, 1004, contentPreview = "有關 App、瀏覽器、網路設定可以集中問。", lastReplyTime = "32 分鐘前")
        )
    }
}

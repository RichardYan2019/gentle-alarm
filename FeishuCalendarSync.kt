package com.gentlealarm.calendar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class FeishuCalendarSync(private val context: Context) {

    // 飞书开放平台 OAuth2 配置
    // 需要在 https://open.feishu.cn 创建应用并获取
    private val appId = "YOUR_APP_ID"
    private val appSecret = "YOUR_APP_SECRET"

    // 获取 tenant_access_token
    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val url = URL("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.write(
            """{"app_id":"$appId","app_secret":"$appSecret"}""".toByteArray()
        )
        val resp = JSONObject(conn.inputStream.bufferedReader().readText())
        resp.getString("tenant_access_token")
    }

    // 拉取用户日历中未来7天的事件，返回 (标题, 开始时间戳ms) 列表
    suspend fun fetchUpcomingEvents(userAccessToken: String): List<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis() / 1000
            val weekLater = now + 7 * 86400
            val url = URL(
                "https://open.feishu.cn/open-apis/calendar/v4/calendars/primary/events" +
                "?start_time=$now&end_time=$weekLater&page_size=50"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $userAccessToken")
            val resp = JSONObject(conn.inputStream.bufferedReader().readText())
            val items = resp.getJSONObject("data").getJSONArray("items")
            (0 until items.length()).map { i ->
                val event = items.getJSONObject(i)
                val summary = event.optString("summary", "日程")
                val startTs = event.getJSONObject("start_time")
                    .optLong("timestamp", 0L) * 1000L
                summary to startTs
            }.filter { it.second > System.currentTimeMillis() }
        }
}

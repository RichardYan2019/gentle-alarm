package com.gentlealarm.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gentlealarm.alarm.Alarm
import com.gentlealarm.alarm.AlarmScheduler
import com.gentlealarm.calendar.FeishuCalendarSync
import com.gentlealarm.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scheduler by lazy { AlarmScheduler(this) }
    private val feishu by lazy { FeishuCalendarSync(this) }
    private val alarms = mutableListOf<Alarm>()
    private var alarmIdCounter = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAddAlarm.setOnClickListener { showTimePicker() }
        binding.btnSyncFeishu.setOnClickListener { syncFeishu() }
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        android.app.TimePickerDialog(this, { _, hour, minute ->
            val alarm = Alarm(
                id = alarmIdCounter++,
                hour = hour,
                minute = minute,
                label = "温柔起床"
            )
            alarms.add(alarm)
            scheduler.schedule(alarm)
            refreshList()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun syncFeishu() {
        // 实际使用时需先完成 OAuth 登录拿到 userAccessToken
        // 这里用 demo token 演示流程
        val userToken = getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("feishu_token", "") ?: ""
        if (userToken.isEmpty()) {
            binding.tvStatus.text = "请先登录飞书授权"
            return
        }
        lifecycleScope.launch {
            runCatching {
                val events = feishu.fetchUpcomingEvents(userToken)
                events.forEach { (title, tsMs) ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tsMs }
                    // 提前30分钟设置闹钟
                    cal.add(Calendar.MINUTE, -30)
                    val alarm = Alarm(
                        id = alarmIdCounter++,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        label = title
                    )
                    alarms.add(alarm)
                    scheduler.schedule(alarm)
                }
                binding.tvStatus.text = "已同步 ${events.size} 个日程"
                refreshList()
            }.onFailure {
                binding.tvStatus.text = "同步失败: ${it.message}"
            }
        }
    }

    private fun refreshList() {
        binding.tvAlarmList.text = alarms.joinToString("\n") { a ->
            "${"%02d".format(a.hour)}:${"%02d".format(a.minute)}  ${a.label}"
        }
    }
}

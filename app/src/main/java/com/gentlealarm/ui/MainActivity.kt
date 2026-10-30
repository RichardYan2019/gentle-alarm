package com.gentlealarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gentlealarm.alarm.Alarm
import com.gentlealarm.alarm.AlarmScheduler
import com.gentlealarm.calendar.SystemCalendarSync
import com.gentlealarm.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scheduler by lazy { AlarmScheduler(this) }
    private val alarms = mutableListOf<Alarm>()
    private var alarmIdCounter = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        if (!scheduler.canScheduleExact()) {
            binding.tvStatus.text = "系统未允许精确闹钟，触发时间可能不准"
        }

        binding.btnAddAlarm.setOnClickListener { showTimePicker() }
        binding.btnSyncCalendar.setOnClickListener { syncSystemCalendar() }
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

    /**
     * 读取系统日历未来 7 天的日程，每条提前 [LEAD_MINUTES] 分钟设置一个闹钟。
     */
    private fun syncSystemCalendar() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_READ_CALENDAR)
            return
        }

        lifecycleScope.launch {
            runCatching { SystemCalendarSync(contentResolver).fetchUpcomingEvents() }
                .onSuccess { events ->
                    if (events.isEmpty()) {
                        binding.tvStatus.text = "未来 7 天没有日程"
                        return@onSuccess
                    }
                    events.forEach { event ->
                        val ringAt = Calendar.getInstance().apply {
                            timeInMillis = event.beginMillis
                            add(Calendar.MINUTE, -LEAD_MINUTES)
                        }
                        val alarm = Alarm(
                            id = alarmIdCounter++,
                            hour = ringAt.get(Calendar.HOUR_OF_DAY),
                            minute = ringAt.get(Calendar.MINUTE),
                            label = event.title
                        )
                        alarms.add(alarm)
                        scheduler.schedule(alarm)
                    }
                    binding.tvStatus.text = "已按 ${events.size} 个日程设置闹钟"
                    refreshList()
                }
                .onFailure {
                    binding.tvStatus.text = "读取日历失败：${it.message}"
                }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_CALENDAR &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // 用户刚授权，继续刚才中断的同步
            syncSystemCalendar()
        }
    }

    /**
     * Android 13(T) 起 POST_NOTIFICATIONS 是运行时权限；未授权时前台服务通知不会显示。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    private fun refreshList() {
        binding.tvAlarmList.text = alarms.joinToString("\n") { a ->
            "${"%02d".format(a.hour)}:${"%02d".format(a.minute)}  ${a.label}"
        }
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        private const val REQUEST_READ_CALENDAR = 1002

        /** 日程开始前多少分钟响铃。 */
        private const val LEAD_MINUTES = 30
    }
}

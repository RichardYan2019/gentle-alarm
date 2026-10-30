package com.gentlealarm.ui

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gentlealarm.alarm.Alarm
import com.gentlealarm.alarm.AlarmScheduler
import com.gentlealarm.alarm.BatteryOptimization
import com.gentlealarm.alarm.RepeatRule
import com.gentlealarm.calendar.SystemCalendarSync
import com.gentlealarm.data.AlarmRepository
import com.gentlealarm.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { AlarmRepository.get(this) }
    private val scheduler by lazy { AlarmScheduler(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        updateStatusHints()

        binding.btnAddAlarm.setOnClickListener { showTimePicker() }
        binding.btnSyncCalendar.setOnClickListener { syncSystemCalendar() }
        binding.btnBackground.setOnClickListener { requestBackgroundPermission() }

        lifecycleScope.launch {
            repository.observeAlarms().collect { renderAlarms(it) }
        }
    }

    // ---------- 列表 ----------

    private fun renderAlarms(alarms: List<Alarm>) {
        binding.alarmListContainer.removeAllViews()
        if (alarms.isEmpty()) {
            binding.alarmListContainer.addView(buildRow("还没有闹钟，点上面「+ 新建闹钟」", null))
            return
        }
        alarms.forEach { alarm ->
            binding.alarmListContainer.addView(buildRow(describe(alarm), alarm))
        }
    }

    private fun buildRow(rowText: String, alarm: Alarm?): TextView = TextView(this).apply {
        text = rowText
        textSize = 16f
        setPadding(0, 28, 0, 28)
        if (alarm != null) setOnClickListener { showActions(alarm) }
    }

    private fun describe(alarm: Alarm): String {
        val time = "%02d:%02d".format(alarm.hour, alarm.minute)
        val name = alarm.label.ifEmpty { "闹钟" }
        val state = if (alarm.enabled) "" else "（已关闭）"
        return "$time  $name  ·  ${repeatLabel(alarm)}$state"
    }

    private fun repeatLabel(alarm: Alarm): String = when (alarm.repeatRule) {
        RepeatRule.ONCE -> "仅一次"
        RepeatRule.DAILY -> "每天"
        RepeatRule.WEEKDAYS -> "工作日"
        RepeatRule.WEEKENDS -> "休息日"
        RepeatRule.HOLIDAYS -> "法定节假日"
        RepeatRule.CUSTOM -> weekdaysText(alarm.weekdaysMask)
    }

    private fun weekdaysText(mask: Int): String {
        val names = arrayOf("一", "二", "三", "四", "五", "六", "日")
        val picked = names.filterIndexed { index, _ -> ((mask shr index) and 1) == 1 }
        return if (picked.isEmpty()) "未选择" else "周" + picked.joinToString("、")
    }

    private fun showActions(alarm: Alarm) {
        val items = arrayOf(
            if (alarm.enabled) "关闭这个闹钟" else "重新启用",
            "删除"
        )
        AlertDialog.Builder(this)
            .setTitle(
                "%02d:%02d  %s".format(alarm.hour, alarm.minute, alarm.label.ifEmpty { "闹钟" })
            )
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggle(alarm)
                    1 -> remove(alarm)
                }
            }
            .show()
    }

    private fun toggle(alarm: Alarm) {
        lifecycleScope.launch {
            val enable = !alarm.enabled
            repository.setEnabled(alarm, enable)
            if (enable) {
                scheduler.scheduleNext(alarm.copy(enabled = true))
            } else {
                scheduler.cancel(alarm.id)
            }
        }
    }

    private fun remove(alarm: Alarm) {
        lifecycleScope.launch {
            scheduler.cancel(alarm.id)
            repository.delete(alarm)
        }
    }

    // ---------- 新建闹钟 ----------

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute -> pickRepeat(hour, minute) },
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun pickRepeat(hour: Int, minute: Int) {
        val rules = RepeatRule.values()
        val labels = rules.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("重复方式")
            .setItems(labels) { _, which ->
                val rule = rules[which]
                if (rule == RepeatRule.CUSTOM) {
                    pickWeekdays(hour, minute)
                } else {
                    createAlarm(hour, minute, rule, 0)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickWeekdays(hour: Int, minute: Int) {
        val names = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val checked = booleanArrayOf(false, false, false, false, false, true, true)
        AlertDialog.Builder(this)
            .setTitle("选择星期")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                var mask = 0
                checked.forEachIndexed { index, isChecked ->
                    if (isChecked) mask = mask or (1 shl index)
                }
                if (mask == 0) {
                    toast("至少要选一天")
                } else {
                    createAlarm(hour, minute, RepeatRule.CUSTOM, mask)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createAlarm(hour: Int, minute: Int, rule: RepeatRule, mask: Int) {
        lifecycleScope.launch {
            val alarm = Alarm(
                hour = hour,
                minute = minute,
                label = "温柔起床",
                repeatRule = rule,
                weekdaysMask = mask
            )
            val id = repository.save(alarm)
            val saved = alarm.copy(id = id)
            val triggerAt = scheduler.scheduleNext(saved)
            binding.tvStatus.text = if (triggerAt == null) {
                "这条重复规则永远匹配不到日期"
            } else {
                "已设置：${describe(saved)}"
            }
        }
    }

    // ---------- 日历同步 ----------

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
                    var scheduled = 0
                    events.forEach { event ->
                        val triggerAt = event.beginMillis - LEAD_MINUTES * 60_000L
                        if (triggerAt <= System.currentTimeMillis()) return@forEach

                        val at = Calendar.getInstance().apply { timeInMillis = triggerAt }
                        val alarm = Alarm(
                            hour = at.get(Calendar.HOUR_OF_DAY),
                            minute = at.get(Calendar.MINUTE),
                            label = event.title,
                            repeatRule = RepeatRule.ONCE,
                            onceAtMillis = triggerAt
                        )
                        val id = repository.save(alarm)
                        if (scheduler.scheduleNext(alarm.copy(id = id)) != null) scheduled++
                    }
                    binding.tvStatus.text = "已按 $scheduled 个日程设置闹钟"
                }
                .onFailure { binding.tvStatus.text = "读取日历失败：${it.message}" }
        }
    }

    // ---------- 权限 / 状态 ----------

    private fun requestBackgroundPermission() {
        if (BatteryOptimization.isIgnoring(this)) {
            toast("已经是「不优化」状态，不用再设置")
        } else {
            BatteryOptimization.requestIgnore(this)
        }
    }

    private fun updateStatusHints() {
        when {
            !scheduler.canScheduleExact() ->
                binding.tvStatus.text = "系统未允许精确闹钟，触发时间可能不准"
            !BatteryOptimization.isIgnoring(this) ->
                binding.tvStatus.text = "未豁免电池优化，深度睡眠时闹钟可能延迟"
            else -> Unit
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        private const val REQUEST_READ_CALENDAR = 1002

        /** 日程开始前多少分钟响铃。 */
        private const val LEAD_MINUTES = 30L
    }
}

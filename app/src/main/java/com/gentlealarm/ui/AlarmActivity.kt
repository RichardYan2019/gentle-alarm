package com.gentlealarm.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.gentlealarm.alarm.Alarm
import com.gentlealarm.alarm.AlarmScheduler
import com.gentlealarm.alarm.AlarmService
import com.gentlealarm.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var alarm: Alarm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏上方显示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarm = intent.getSerializableExtra("alarm") as Alarm

        binding.tvTime.text = "%02d:%02d".format(alarm.hour, alarm.minute)
        binding.tvLabel.text = alarm.label.ifEmpty { "起床啦" }
        updateSnoozeButton()

        binding.btnDismiss.setOnClickListener { dismiss() }
        binding.btnSnooze.setOnClickListener { snooze() }
    }

    private fun updateSnoozeButton() {
        val remaining = alarm.maxSnooze - alarm.snoozeCount
        if (remaining <= 0) {
            binding.btnSnooze.isEnabled = false
            binding.btnSnooze.text = "不能再睡了"
        } else {
            val nextInterval = maxOf(2, alarm.snoozeIntervalMin - 1)
            binding.btnSnooze.text = "再睡 ${nextInterval} 分钟（还剩 $remaining 次）"
        }
    }

    private fun dismiss() {
        stopService(android.content.Intent(this, AlarmService::class.java))
        finish()
    }

    private fun snooze() {
        if (alarm.snoozeCount >= alarm.maxSnooze) return
        stopService(android.content.Intent(this, AlarmService::class.java))
        AlarmScheduler(this).scheduleSnooze(alarm)
        finish()
    }
}

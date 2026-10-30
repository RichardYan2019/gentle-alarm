package com.gentlealarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.gentlealarm.ui.AlarmActivity

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarm = intent?.getSerializableExtra("alarm") as? Alarm ?: return START_NOT_STICKY
        stopped = false

        startForegroundCompat(alarm)
        startGentleWakeup(alarm)
        showAlarmScreenIfAllowed(alarm)

        return START_NOT_STICKY
    }

    /**
     * Android 14 起，声明了 foregroundServiceType="mediaPlayback" 的服务必须用带类型的
     * startForeground 重载启动，否则抛出 SecurityException。
     */
    private fun startForegroundCompat(alarm: Alarm) {
        val notification = buildNotification(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                alarm.id,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(alarm.id, notification)
        }
    }

    private fun startGentleWakeup(alarm: Alarm) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        // 从音量 0 开始，在 fadeInDurationSec 秒内线性增大到最大音量
        val player = MediaPlayer()
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(this, defaultAlarmUri())
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
        }.onFailure {
            // 设备没有默认闹铃时不要弄崩服务，继续震动和显示界面
            runCatching { player.release() }
        }

        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0) }

        val steps = alarm.fadeInDurationSec.coerceAtLeast(1)
        for (step in 1..steps) {
            handler.postDelayed({
                val vol = (maxVol * step / steps.toFloat()).toInt()
                runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) }
            }, step * 1000L)
        }

        vibrator = acquireVibrator()
        startProgressiveVibration()
    }

    /**
     * Android 12 起 Vibrator 服务改由 VibratorManager 提供；旧写法在 S+ 上已废弃。
     */
    private fun acquireVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

    private fun startProgressiveVibration() {
        val haptics = vibrator ?: return
        runCatching {
            // 渐进震动：从轻到重
            val pattern = longArrayOf(0, 200, 800, 400, 600, 600, 400, 800, 200)
            val amplitudes = intArrayOf(0, 50, 0, 100, 0, 150, 0, 200, 0)
            haptics.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0))
        }
    }

    private fun alarmScreenIntent(alarm: Alarm): Intent =
        Intent(this, AlarmActivity::class.java).apply {
            putExtra("alarm", alarm)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /**
     * Android 10(Q) 起禁止后台直接启动 Activity，此时只能靠通知的 full-screen intent
     * （见 [buildNotification]）。Q 以下直接启动仍然可用，且不会和全屏意图重复拉起。
     */
    private fun showAlarmScreenIfAllowed(alarm: Alarm) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runCatching { startActivity(alarmScreenIntent(alarm)) }
        }
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val channelId = "alarm_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "闹钟", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val builder = Notification.Builder(this, channelId)
            .setContentTitle("温柔起床")
            .setContentText(alarm.label.ifEmpty { "%02d:%02d".format(alarm.hour, alarm.minute) })
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)

        // 锁屏/息屏时全屏拉起闹钟界面（Android 10+ 唯一可靠的途径）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fullScreenIntent = PendingIntent.getActivity(
                this,
                alarm.id,
                alarmScreenIntent(alarm),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        return builder.build()
    }

    private fun defaultAlarmUri(): Uri = Settings.System.DEFAULT_ALARM_ALERT_URI

    /** 幂等：dismiss/snooze 与 onDestroy 都会走到这里。 */
    fun stopAlarm() {
        if (stopped) return
        stopped = true

        handler.removeCallbacksAndMessages(null)
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

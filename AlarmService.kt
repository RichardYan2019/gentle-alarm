package com.gentlealarm.alarm

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.os.VibrationEffect
import com.gentlealarm.ui.AlarmActivity

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentAlarm: Alarm? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarm = intent?.getSerializableExtra("alarm") as? Alarm ?: return START_NOT_STICKY
        currentAlarm = alarm

        startForeground(alarm.id, buildNotification(alarm))
        startGentleWakeup(alarm)
        launchAlarmScreen(alarm)

        return START_NOT_STICKY
    }

    private fun startGentleWakeup(alarm: Alarm) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        // 从音量0开始，在 fadeInDurationSec 秒内线性增大到最大音量
        mediaPlayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_ALARM)
            setDataSource(getDefaultAlarmUri())
            isLooping = true
            prepare()
            start()
        }
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)

        val steps = alarm.fadeInDurationSec
        val intervalMs = 1000L
        for (step in 1..steps) {
            handler.postDelayed({
                val vol = (maxVol * step / steps.toFloat()).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
            }, step * intervalMs)
        }

        // 渐进震动：从轻到重
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 200, 800, 400, 600, 600, 400, 800, 200)
            val amplitudes = intArrayOf(0, 50, 0, 100, 0, 150, 0, 200, 0)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0))
        }
    }

    private fun launchAlarmScreen(alarm: Alarm) {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("alarm", alarm)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun buildNotification(alarm: Alarm): Notification {
        val channelId = "alarm_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "闹钟", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("温柔起床")
            .setContentText(alarm.label.ifEmpty { "${alarm.hour}:${"%02d".format(alarm.minute)}" })
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }

    private fun getDefaultAlarmUri(): String {
        val uri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        return uri.toString()
    }

    fun stopAlarm() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { stopAlarm() }
}

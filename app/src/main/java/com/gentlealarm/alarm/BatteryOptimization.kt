package com.gentlealarm.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 电池优化豁免。
 *
 * 说明：Android **没有**「加入白名单就永不被杀」的 API，任何声称能做到的方案
 * 都是在赌厂商 ROM 不清理进程。真正保证闹钟按时响的是
 * 精确闹钟（setExactAndAllowWhileIdle）+ 前台服务，这里只是减少 Doze 下的延迟，
 * 以及引导用户把应用从厂商的后台清理名单里放出来。
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 直接申请豁免；部分厂商 ROM 会拦截这个 Intent，拦截时退回到电池优化设置列表，
     * 由用户手动选择「不优化」。
     */
    fun requestIgnore(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val opened = runCatching { context.startActivity(direct) }.isSuccess
        if (!opened) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }
}

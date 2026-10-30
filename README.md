# 温柔闹钟 GentleAlarm

一个把「渐进唤醒」做成卖点的 Android 闹钟：音量从 0 逐渐增大、震动由轻到重、贪睡间隔逐次缩短，
并且支持把系统日历的日程自动变成提前 30 分钟的闹钟。

## 功能

- **渐进唤醒**：`fadeInDurationSec` 秒内音量线性升到最大，震动振幅逐段增强
- **贪睡递减**：5 → 4 → 3 → 2 分钟，最多 3 次，越睡越难
- **重复规则**：仅一次 / 每天 / 工作日 / 休息日 / 法定节假日 / 自定义星期
- **持久化**：闹钟存在 Room 数据库里，杀进程、重启都不丢
- **开机恢复**：重启、应用更新、改时间/改时区后自动重排（`BootReceiver`）
- **系统日历同步**：读取未来 7 天日程，每条提前 30 分钟生成一次性闹钟
- **锁屏全屏提醒**：通过 full-screen intent 在锁屏/息屏时直接拉起闹钟界面

## 打包 APK 的三种方式

### 方式 A：Android Studio（推荐，最快）

1. 下载安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio → `File` → `Open` → 选择本项目根目录（`D:\Deepseek Flash`）
3. 等待 Gradle 自动同步（首次约 5-10 分钟，会自动下载依赖）
4. 菜单栏 → `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
5. 构建完成后点弹窗里的 `locate`，APK 在 `app/build/outputs/apk/debug/app-debug.apk`
6. 把 APK 复制到手机安装即可

### 方式 B：命令行（已有 Android SDK 和 JDK 17 的话）

```bash
cd "D:/Deepseek Flash"
gradlew.bat assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

### 方式 C：GitHub Actions 云端构建（无需本地装环境）

1. 把仓库推到 GitHub
2. Actions 会自动跑 `.github/workflows/build.yml`（只监听 `main` 分支，也可手动 `workflow_dispatch`）
3. 在 Actions 页面下载产物 `gentle-alarm-debug`

## 安装到手机

1. 手机进入 `设置` → `安全` → 允许"未知来源"应用安装
2. 把 APK 传到手机（微信/QQ/数据线都行）
3. 点击安装

## 首次使用建议

1. 打开 App，允许**通知**权限（Android 13+ 必须，否则前台服务通知不显示）
2. 点「**允许后台运行**」并同意「忽略电池优化」，否则深度睡眠时闹钟可能延迟
3. 如果系统限制了精确闹钟，在设置里允许「闹钟和提醒」
4. 点「同步系统日历」授予「读取日历」权限，日程会被自动排成闹钟

## 节假日与调休数据（需要每年维护）

「工作日 / 休息日 / 法定节假日」三种重复规则依赖 `app/src/main/assets/holidays.json`。
Android 没有「今天是不是法定工作日」的 API，所以这份数据必须内置：

```json
{
  "2026": {
    "holidays": ["2026-01-01", "..."],
    "workdays": ["2026-01-04", "..."]
  }
}
```

- `holidays`：放假的日子（含调休放假）
- `workdays`：本来是周末、但需要上班的调休日

当前内置的是 **2026 年** 数据，来源：国务院办公厅《关于2026年部分节假日安排的通知》
（国办发明电〔2025〕7号，2025-11-04）。**国务院通常在每年 11 月发布下一年安排**，
届时把新的年份节点加进这个 JSON 即可，不用改代码。

## 已知限制

- 日历同步只读取系统日历，不写入；需要 `READ_CALENDAR` 权限
- **「永不被杀」不存在**：Android 没有这种白名单 API。本应用靠「精确闹钟 + 前台服务 + 电池优化豁免」
  保证准时，厂商 ROM（MIUI/EMUI/ColorOS 等）仍需在系统设置里手动允许「自启动 / 后台运行」
- 贪睡的间隔没有写回数据库：重启后该闹钟的贪睡计数从 0 开始

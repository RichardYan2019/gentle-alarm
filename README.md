# 温柔闹钟 GentleAlarm

## 打包 APK 的三种方式

### 方式 A：Android Studio（推荐，最快）

1. 下载安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio → `File` → `Open` → 选择 `D:/project/gentle-alarm` 文件夹
3. 等待 Gradle 自动同步（首次约 5-10 分钟，会自动下载依赖）
4. 菜单栏 → `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
5. 构建完成后点弹窗里的 `locate`，APK 在 `app/build/outputs/apk/debug/app-debug.apk`
6. 把 APK 复制到手机安装即可

### 方式 B：命令行（已有 Android SDK 和 JDK 的话）

```bash
cd D:/project/gentle-alarm
gradlew.bat assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

### 方式 C：GitHub Actions 云端构建（无需本地装环境）

1. 把 `gentle-alarm` 文件夹推到 GitHub 仓库
2. Actions 会自动跑 `.github/workflows/build.yml`
3. 在 Actions 页面下载产物 `gentle-alarm-debug.apk`

## 安装到手机

1. 手机进入 `设置` → `安全` → 允许"未知来源"应用安装
2. 把 APK 传到手机（微信/QQ/数据线都行）
3. 点击安装

## 试用建议

- **闹钟测试**：可以先设一个 1 分钟后的闹钟体验渐进唤醒
- **飞书同步**：需要先配置 `FeishuCalendarSync.kt` 里的 `appId` / `appSecret`，并实现 OAuth 登录（demo 暂未做完整登录流程）

## 已知 demo 限制

- 闹钟没有持久化：app 进程被杀后闹钟丢失（后续加 Room 数据库解决）
- 飞书同步需要你自己申请开放平台应用
- 没有开机自启恢复闹钟（待实现 BootReceiver）

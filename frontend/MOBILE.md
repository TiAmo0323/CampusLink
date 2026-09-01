# CampusLink Android 移动端

前端使用 Capacitor 将同一套 Vue 3 页面封装为标准 Android 应用。网页端继续使用 history 路由，Android 原生容器自动改用 hash 路由。

## 已完成适配

- Android 工程目录：`frontend/android/`
- 应用 ID：`com.campuslink.app`
- 最低系统：Android 7（API 24）
- 编译/目标 SDK：API 36
- Android 模拟器 API：`http://10.0.2.2:8080`
- 支持状态栏/底部手势安全区、触控按钮、移动弹窗、窄屏表单、底部导航和图片上传预览。
- HTTP 仅在 debug 构建中开放；release 构建应改用 HTTPS 后端。

## 环境准备

安装 Android Studio 2025.2.1 或更高版本，并在 SDK Manager 中安装：

- Android SDK Platform 36
- Android SDK Build-Tools
- Android SDK Platform-Tools
- Android Emulator
- 一个 API 24 或更高版本的模拟器镜像（建议 API 36）

Android Studio 会提供合适的 JDK，无需单独配置项目内 JDK。

## 模拟器运行

先启动后端：

```powershell
cd D:\CampusLink\backend
mvn spring-boot:run
```

再同步并打开 Android 工程：

```powershell
cd D:\CampusLink\frontend
npm install
npm run android:sync
npm run android:open
```

在 Android Studio 的 Device Manager 创建并启动模拟器，然后点击 Run。也可以直接执行：

```powershell
npm run android:run
```

默认 Android 模拟器使用 `10.0.2.2` 访问开发电脑，因此不需要修改后端地址。

## 真机运行

1. 将 `.env.device.example` 复制为 `.env.android`。
2. 把 `192.168.1.100` 替换成开发电脑的局域网 IPv4 地址。
3. 确保手机与电脑处于同一网络，并允许防火墙访问后端 8080 端口。
4. 重新执行 `npm run android:sync`，再从 Android Studio 安装到设备。

## 构建 APK

安装 SDK 后，可在 Android Studio 中使用 **Build > Build APK(s)**，或执行：

```powershell
cd D:\CampusLink\frontend\android
.\gradlew.bat assembleDebug
```

调试 APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。

正式发布时必须将 `.env.android` 的 API 地址改为 HTTPS，并配置 Android 签名后构建 release 包。

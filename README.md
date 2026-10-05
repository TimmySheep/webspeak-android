[简体中文](#简体中文) | [English](#english)

# WebSpeak Android

## 简体中文

WebSpeak 的原生 Android 客户端。使用 Kotlin、Jetpack Compose 和 Material 3；复用 WebSpeak 网关的 join-ticket、WebSocket 控制协议与 WebRTC 语音/屏幕共享信令。

当前状态：**Public Alpha / 开发中**。功能范围、已知限制与后续计划见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。欢迎通过 Issue 报告问题，或按 [`CONTRIBUTING.md`](CONTRIBUTING.md) 提交改进。

### 工具链

- JDK 17
- Android SDK Platform 36 / Build Tools 36
- Gradle Wrapper 8.13
- Android Gradle Plugin 8.13.2、Kotlin 2.3.21、Compose BOM 2025.08.00（与 API 36 工具链兼容）

本机已有 Android SDK 时，可按机器实际位置设置 `ANDROID_HOME`；不要把 `local.properties` 或本机绝对路径提交进仓库。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Android 麦克风权限由用户在前台明确授予。持续语音使用前台服务和常驻通知。

### 应用图标

启动器与应用内品牌图标使用 WebSpeak 浏览器/PWA 当前的 1024px 图标资源，未另行绘制替代标志。

### 许可证

应用源码采用 AGPL-3.0-only。直接依赖及发行前许可核验说明见 [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md)。

## English

WebSpeak's native Android client, built with Kotlin, Jetpack Compose, and Material 3. It reuses the WebSpeak gateway's join-ticket flow, WebSocket control protocol, and WebRTC signaling for voice and screen sharing.

Status: **Public Alpha / In Development**. See [`docs/ROADMAP.md`](docs/ROADMAP.md) for scope, known limitations, and planned work. Report issues on GitHub, or contribute changes as described in [`CONTRIBUTING.md`](CONTRIBUTING.md).

### Toolchain

- JDK 17
- Android SDK Platform 36 / Build Tools 36
- Gradle Wrapper 8.13
- Android Gradle Plugin 8.13.2, Kotlin 2.3.21, and Compose BOM 2025.08.00 (compatible with the API 36 toolchain)

If the Android SDK is already installed, set `ANDROID_HOME` to its location on your machine. Do not commit `local.properties` or machine-specific absolute paths.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Microphone access must be explicitly granted by the user while the app is in the foreground. Ongoing voice sessions use an Android foreground service and a persistent notification.

### App icon

The launcher and in-app brand icon use the current 1024px WebSpeak browser/PWA icon asset; no replacement logo has been drawn.

### License

The application source is licensed under AGPL-3.0-only. See [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md) for direct dependencies and pre-release license review notes.

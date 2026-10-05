# Third-party notices / 第三方声明

## English

The following are direct dependencies declared by this project. Versions are maintained in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Android Gradle Plugin and Kotlin Gradle plugins are build-time tools and are not bundled in the app.

### Application dependencies

| Component | License | Upstream |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle, Navigation, Compose UI and Material 3 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| Kotlin Coroutines for Android | Apache-2.0 | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| OkHttp | Apache-2.0 | [OkHttp](https://github.com/square/okhttp) |
| WebRTC Android SDK (`io.github.webrtc-sdk:android`) | BSD-3-Clause (artifact metadata) | [webrtc-sdk/android](https://github.com/webrtc-sdk/android) |

### Test dependencies

| Component | License | Upstream |
| --- | --- | --- |
| JUnit 4 | EPL-1.0 | [JUnit4](https://github.com/junit-team/junit4) |
| AndroidX Test and Espresso | Apache-2.0 | [AndroidX Test](https://developer.android.com/training/testing) |

This list identifies direct dependencies; transitive components may have their own notices and license terms. Before distributing a packaged APK/AAB, re-check the resolved dependency graph and include the applicable upstream license texts and notices, including those distributed with the WebRTC artifact. The application's own source is licensed under AGPL-3.0-only; see [`LICENSE`](../LICENSE).

## 简体中文

以下为本项目声明的直接依赖。版本由 [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) 管理。Android Gradle Plugin 和 Kotlin Gradle 插件是构建时工具，不会打包进应用。

### 应用依赖

| 组件 | 许可证 | 上游项目 |
| --- | --- | --- |
| AndroidX Core、Activity、Lifecycle、Navigation、Compose UI 和 Material 3 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| Kotlin Coroutines for Android | Apache-2.0 | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| OkHttp | Apache-2.0 | [OkHttp](https://github.com/square/okhttp) |
| WebRTC Android SDK（`io.github.webrtc-sdk:android`） | BSD-3-Clause（制品元数据） | [webrtc-sdk/android](https://github.com/webrtc-sdk/android) |

### 测试依赖

| 组件 | 许可证 | 上游项目 |
| --- | --- | --- |
| JUnit 4 | EPL-1.0 | [JUnit4](https://github.com/junit-team/junit4) |
| AndroidX Test 和 Espresso | Apache-2.0 | [AndroidX Test](https://developer.android.com/training/testing) |

本清单只列出直接依赖；传递依赖可能适用各自的声明与许可条款。分发 APK/AAB 前，请重新检查实际解析的完整依赖图，并附上适用的上游许可证文本与声明，包括 WebRTC 制品随附的材料。应用自身源码采用 AGPL-3.0-only，见 [`LICENSE`](../LICENSE)。

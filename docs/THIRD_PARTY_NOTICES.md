# Third-party notices

The following are direct dependencies declared by this project. Versions are maintained in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Android Gradle Plugin and Kotlin Gradle plugins are build-time tools and are not bundled in the app.

## Application dependencies

| Component | License | Upstream |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle, Navigation, Compose UI and Material 3 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) |
| Kotlin Coroutines for Android | Apache-2.0 | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| OkHttp | Apache-2.0 | [OkHttp](https://github.com/square/okhttp) |
| WebRTC Android SDK (`io.github.webrtc-sdk:android`) | BSD-3-Clause (artifact metadata) | [webrtc-sdk/android](https://github.com/webrtc-sdk/android) |

## Test dependencies

| Component | License | Upstream |
| --- | --- | --- |
| JUnit 4 | EPL-1.0 | [JUnit4](https://github.com/junit-team/junit4) |
| AndroidX Test and Espresso | Apache-2.0 | [AndroidX Test](https://developer.android.com/training/testing) |

This list identifies direct dependencies; transitive components may have their own notices and license terms. Before distributing a packaged APK/AAB, re-check the resolved dependency graph and include the applicable upstream license texts and notices, including those distributed with the WebRTC artifact. The application's own source is licensed under AGPL-3.0-only; see [`LICENSE`](../LICENSE).

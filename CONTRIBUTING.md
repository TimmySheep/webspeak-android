# Contributing / 参与贡献

## 简体中文

感谢参与 WebSpeak Android。项目使用 Kotlin、Jetpack Compose 和 Material 3；网关及 TeamSpeak 集成由现有 WebSpeak 服务提供。

### 本地构建与测试

- JDK 17、Android SDK Platform 36、Build Tools 36。
- 使用仓库自带 Gradle Wrapper，不需要安装全局 Gradle。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

请在提交前运行相关测试，并在 Pull Request 中说明未能验证的设备或网关行为。静态构建通过不等于真实设备端到端验收通过。

### 提交改动

- 较大的功能或协议变更请先通过 GitHub Issue 讨论，再提交 Pull Request。
- 保持改动聚焦，并补充或更新对应测试与用户可见文档。
- 不提交本机配置、构建产物、密钥、邀请 Token、密码、TeamSpeak identity 或包含这些信息的日志。
- 网络连接必须使用系统正常 TLS 校验；不得加入信任所有证书或绕过网关策略的实现。
- 后台语音必须遵守 Android 前台服务和权限要求，不承诺绕过系统或厂商的停止策略。

## English

Thanks for contributing to WebSpeak Android. The project uses Kotlin, Jetpack Compose, and Material 3. The existing WebSpeak service provides the gateway and TeamSpeak integration.

### Build and test locally

- JDK 17, Android SDK Platform 36, and Build Tools 36.
- Use the Gradle Wrapper included in this repository; a global Gradle installation is not required.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Run the relevant tests before submitting. In your pull request, describe any device or gateway behavior you could not verify. A successful static build does not constitute end-to-end validation on a real device.

### Submit changes

- Discuss larger features or protocol changes in a GitHub Issue before opening a pull request.
- Keep changes focused, and add or update the relevant tests and user-facing documentation.
- Do not commit local configuration, build outputs, secrets, invitation tokens, passwords, TeamSpeak identities, or logs containing them.
- Network connections must use normal system TLS validation. Do not trust all certificates or bypass gateway access policies.
- Background voice must follow Android foreground-service and permission requirements. Do not claim that the app can bypass Android or device-vendor process restrictions.

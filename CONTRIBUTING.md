# Contributing

感谢参与 WebSpeak Android。项目使用 Kotlin、Jetpack Compose 和 Material 3；网关及 TeamSpeak 集成由现有 WebSpeak 服务提供。

## 本地构建与测试

- JDK 17、Android SDK Platform 36、Build Tools 36。
- 使用仓库自带 Gradle Wrapper，不需要安装全局 Gradle。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

请在提交前运行相关测试，并在 Pull Request 中说明未能验证的设备或网关行为。静态构建通过不等于真实设备端到端验收通过。

## 提交改动

- 通过 GitHub Issue 描述较大的功能或协议变更，再提交 Pull Request。
- 保持改动聚焦，补充或更新对应测试与用户可见文档。
- 不提交本机配置、构建产物、密钥、邀请 Token、密码、TeamSpeak identity 或包含这些信息的日志。
- 网络连接必须使用系统正常 TLS 校验；不得加入信任所有证书或绕过网关策略的实现。
- 后台语音必须遵守 Android 前台服务和权限要求，不承诺绕过系统或厂商的停止策略。

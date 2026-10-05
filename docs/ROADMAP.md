# Public Alpha 状态与路线图 / Public Alpha Status and Roadmap

## 简体中文

WebSpeak Android 目前处于早期开发阶段。这里记录公开用户可见的能力边界；实现存在不代表所有网关、设备和网络组合均已验证。

### 当前方向

- **连接与身份：** 原生连接界面、网关配置和 Android Keystore 身份保存已有初步实现；不同网关和身份恢复流程仍需更多设备验证。
- **语音：** 原生 WebRTC 与前台服务已接入。连接恢复、蓝牙/耳机路由、长时间锁屏和网络切换仍需系统化测试。
- **频道、成员与聊天：** 原生界面及部分操作已实现；真实网关联调、权限结果和实时消息流程尚未完整验收。
- **屏幕共享：** Android 屏幕采集路径处于早期阶段；共享观看、音频、全屏和不同设备间的兼容性仍有已知缺口。
- **音频与诊断：** 基本静音、音量及平台音频处理已实现；可选路由、VOX、伴奏和完整 WebRTC 统计尚未完成。
- **本地化与无障碍：** 已有多语言资源和 Material 3 基线；完整翻译、不同屏幕/字体大小和无障碍行为仍需逐项检查。

### 近期优先级

1. 稳定连接、频道/成员操作和实时聊天的网关联调。
2. 在多台真实 Android 设备上验证语音生命周期、音频路由和网络恢复。
3. 修复并验收屏幕共享观看与发送路径。
4. 完成多语言、无障碍和 Release 构建检查。
5. 发布安装包前补全发行所需的第三方归属与许可核验。

请在 GitHub Issues 中附上 Android 版本、设备型号、复现步骤和脱敏日志；不要提交邀请 Token、TeamSpeak identity、密码或完整连接凭据。

## English

WebSpeak Android is in early development. This roadmap describes the capabilities and limitations visible to users. The presence of an implementation does not mean that every gateway, device, and network combination has been validated.

### Current areas of work

- **Connection and identity:** Initial implementations of the native connection screen, gateway configuration, and Android Keystore identity storage are in place. More device testing is needed for different gateways and identity recovery.
- **Voice:** Native WebRTC and a foreground service are integrated. Reconnection, Bluetooth/headset routing, extended screen-off sessions, and network changes still need systematic testing.
- **Channels, members, and chat:** Native screens and some actions are implemented; gateway integration, permission outcomes, and real-time messaging have not been fully validated.
- **Screen sharing:** Android screen capture is at an early stage. Known gaps remain in viewing, audio, full-screen behavior, and compatibility across devices.
- **Audio and diagnostics:** Basic mute, volume, and platform audio processing are implemented. Optional routing, VOX, accompaniment audio, and complete WebRTC statistics are not yet finished.
- **Localization and accessibility:** Multiple-language resources and a Material 3 baseline exist; complete translations, screen/font-size coverage, and accessibility behavior still need review.

### Near-term priorities

1. Stabilize gateway integration for connections, channel/member actions, and real-time chat.
2. Validate voice lifecycle, audio routing, and network recovery on multiple physical Android devices.
3. Fix and validate screen-sharing capture and viewing flows.
4. Complete localization, accessibility, and release-build checks.
5. Finish third-party attribution and license review before distributing an app package.

When filing a GitHub Issue, include the Android version, device model, reproduction steps, and sanitized logs. Do not submit invitation tokens, TeamSpeak identities, passwords, or complete connection credentials.

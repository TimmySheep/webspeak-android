# 原生客户端架构 / Native Client Architecture

## 简体中文

### 边界

- Android 客户端负责原生 Material 3 UI、用户偏好、通知与 Android 音频/屏幕采集生命周期。
- WebSpeak 服务继续负责 TS3/TS6 ServerQuery/SDK 会话与既有控制/共享信令；语音和屏幕媒体按现有 WebRTC 方案点对点传输，不通过 Android UI/WebView 中转。
- HTTP 连接使用 `/api/public-config` 与一次性 `/api/join-ticket`；实时控制连接为 `/ws/voice?ticket=...`。
- join-ticket 当前以 `Origin` 与 `Host` 同源匹配作浏览器 CSRF 防护。原生客户端需要为当前网关 URL 生成同源 Origin；这不是用户身份认证，不得在客户端保存或记录票据。

### Android 分层

- `ui/`：Compose 页面、Material 3 主题、导航与窗口自适应。
- `model/`：连接、成员、频道、聊天、屏幕共享和偏好模型。
- `data/`：网关 HTTP、WebSocket 协议解析和本地历史/偏好存储。
- `service/`：语音前台服务、WebRTC 音频/视频会话、音频路由及通知动作。

服务从前台页面经权限确认后启动；服务显示持续通知，管理 socket/PeerConnection 的创建、网络错误、重连与释放。UI 通过可观察状态订阅会话，页面旋转或切换后台不拥有媒体资源。

### 平台约束

- 麦克风前台服务要求用户授予 `RECORD_AUDIO`，连接从可见 Activity 发起；通知明确显示持续使用状态。系统停止 App、用户强制停止、厂商电池策略和网络切换仍可能中断会话。
- Android 屏幕共享另需 MediaProjection 用户授权和 mediaProjection 前台服务类型；与仅观看共享、语音后台服务分开管理。
- 屏幕流只在服务端转发协商信令；媒体仍由 WebRTC/ICE 对端传输。
- WebSpeak 网关不提供原生 TeamSpeak 客户端本地历史聊天拉取 API；App 只能从首次收到消息后保存自己的本地记录。

## English

### Boundaries

- The Android client owns the native Material 3 UI, user preferences, notifications, and Android audio/screen-capture lifecycles.
- The WebSpeak service continues to own TS3/TS6 ServerQuery/SDK sessions and the existing control and sharing signaling. Voice and screen media use the existing peer-to-peer WebRTC design; they are not relayed through the Android UI or a WebView.
- HTTP calls use `/api/public-config` and the one-time `/api/join-ticket` endpoint. The real-time control connection is `/ws/voice?ticket=...`.
- The join ticket currently uses same-origin matching of `Origin` and `Host` for browser CSRF protection. The native client must generate an `Origin` matching the active gateway URL. This is not user authentication; never persist or log the ticket in the client.

### Android layers

- `ui/`: Compose screens, Material 3 theme, navigation, and adaptive window behavior.
- `model/`: Connection, member, channel, chat, screen-sharing, and preference models.
- `data/`: Gateway HTTP, WebSocket protocol parsing, and local history/preference storage.
- `service/`: Voice foreground service, WebRTC audio/video sessions, audio routing, and notification actions.

The service starts from a foreground screen after permissions are granted. It displays an ongoing notification and manages socket/PeerConnection creation, network errors, reconnection, and release. The UI observes session state; screen rotation or backgrounding does not transfer ownership of media resources to a screen.

### Platform constraints

- A microphone foreground service requires the user to grant `RECORD_AUDIO`, and the connection must be started from a visible Activity. The notification must clearly show ongoing use. System process termination, force-stop, vendor battery policies, and network changes can still interrupt a session.
- Android screen sharing additionally requires user authorization through MediaProjection and a `mediaProjection` foreground-service type. It is managed separately from viewing a share and from the voice background service.
- The server relays screen-sharing negotiation signaling only; media is transmitted between peers using WebRTC/ICE.
- The WebSpeak gateway does not expose an API for a native TeamSpeak client to retrieve prior local chat history. The app can only save messages locally after it first receives them.

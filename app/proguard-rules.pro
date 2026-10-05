# Keep native WebRTC JNI entry points when release shrinking is enabled.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

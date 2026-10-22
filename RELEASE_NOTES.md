## Subcord 1.1.0 — much smaller APK

### Size
- Removed ML Kit Translate / Language-ID native libraries (~60MB of `.so` files)
- Network translation instead (cached in memory)
- Phone ABIs only (`arm64-v8a`, `armeabi-v7a`) — no x86 emulator slices

Release APK should drop from ~67MB to a few MB.

### Privacy note
Screen reading is still local. Only the text of new messages is sent to the translation service. No Discord token or account access.

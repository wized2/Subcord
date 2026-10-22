# Subcord

**Live English subtitles for Discord — on your Android screen.**

Subcord primarily runs **Discord Web** in-app and injects a translator into the HTML chat. A legacy Accessibility overlay for the native Discord app is still available in Settings. It reads **visible** chat text with an AccessibilityService, detects new non-English lines, translates them on-device with [ML Kit](https://developers.google.com/ml-kit/language/translation), and shows the result in a system overlay.

No Discord login. No bot token. No root. No self-bot.

<p align="center">
  <a href="https://github.com/wized2/Subcord/releases/latest"><img alt="Download" src="https://img.shields.io/github/v/release/wized2/Subcord?label=Download%20APK&style=for-the-badge"></a>
  <img alt="License" src="https://img.shields.io/github/license/wized2/Subcord?style=for-the-badge">
</p>

## Features

- Auto-translate newly seen messages in servers, DMs, and group chats (whatever is on screen)
- Floating bubble to pause / resume translations
- Network translation with in-memory cache (no bulky on-device models in the APK)
- In-memory translation cache
- Filters Discord UI chrome (timestamps, buttons, nav labels)
- Works with `com.discord`, Canary, and PTB

## Limits

- Only text currently visible on screen is considered
- Discord UI changes can reduce detection quality
- Cannot auto-send replies as you (self-bot / ToS risk)

## Privacy

- Screen reading runs locally
- Only message text is sent to the translation service; screen reading stays local
- Subcord does not access your Discord account or token

## Setup

1. Install from [Releases](https://github.com/wized2/Subcord/releases)
2. Open **Subcord** → enable **Accessibility** for “Subcord Discord Reader”
3. Allow **Display over other apps**
4. Tap **Start overlay**, open Discord, scroll the chat

Translation requires a network connection. English-looking lines can be skipped in settings.

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease   # uses RELEASE_* env if set
```

### CI signing secrets (Fern-style)

| Secret | Purpose |
|--------|---------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded keystore |
| `RELEASE_STORE_PASSWORD` | Store password |
| `RELEASE_KEY_ALIAS` | Alias |
| `RELEASE_KEY_PASSWORD` | Key password |

Forks and incomplete/invalid keystores fall back to debug signing so CI stays green.

Release notes for tags are taken from `RELEASE_NOTES.md` (or the optional `workflow_dispatch` input).

## License

MIT © wized2

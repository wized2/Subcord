# Subcord

**Live English subtitles for Discord — mobile web, on your phone.**

Subcord embeds **Discord Web in mobile mode**, injects a translator into the chat HTML, and shows English under non-English messages.

- Login works (cookies stay on device)
- Last channel / DM is restored next launch
- No accessibility overlay, no root, no Discord token

## Setup

1. Install from [Releases](https://github.com/wized2/Subcord/releases)
2. Log in
3. Open a channel — translations appear under messages as **EN …**
4. Toolbar: re-inject · reload · settings

## Build

```bash
./gradlew assembleRelease
```

### CI signing secrets

| Secret | Purpose |
|--------|---------|
| `RELEASE_KEYSTORE_BASE64` | Base64 keystore |
| `RELEASE_STORE_PASSWORD` | Store password |
| `RELEASE_KEY_ALIAS` | Alias |
| `RELEASE_KEY_PASSWORD` | Key password |

## License

MIT © wized2

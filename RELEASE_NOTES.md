## Subcord 1.0.1

Live English subtitles for Discord — on your Android screen.

### What’s in this release
- Fixed release signing (valid keystore + CI validation with debug fallback on forks)
- Smarter message scanner: skips Discord chrome, timestamps, and pure UI labels
- Overlay panel duration preference (via prefs)
- Clearer status line for Accessibility + Overlay permissions
- Fern-style CI: optional signing secrets, artifact upload, tag releases from `RELEASE_NOTES.md`

### Setup
1. Install the release APK
2. Enable **Accessibility** for “Subcord Discord Reader”
3. Allow **Display over other apps**
4. Tap **Start overlay**, open Discord

First translation for a new language may download an on-device ML Kit model (one-time network).

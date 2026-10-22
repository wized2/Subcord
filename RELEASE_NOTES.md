## Subcord 1.3.0 — mobile Discord + last channel memory

### Fixed
- **Mobile UI** — uses Android Chrome user-agent so Discord shows the phone chat layout (not desktop sidebar)
- Viewport forced to `device-width`

### Removed
- Legacy Accessibility overlay / floating bubble / related permissions

### New
- **Remembers last channel/DM URL** (`discord.com/channels/...`) and restores it on next open
- Settings: skip-English, show last URL, reset to Home

Still ~1.6MB release.

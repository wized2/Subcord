## Subcord 1.2.0 — Discord Web + HTML injection

### Primary mode
- Opens **Discord Web** inside the app (login works; cookies stay on device)
- Injects a resilient translator script into the page
- Native bridge translates text (Google + MyMemory fallback) and writes **EN** under messages
- Survives SPA navigation with MutationObserver + periodic re-scan + re-inject

### Legacy
- Accessibility overlay for the native Discord app remains in Settings

### Size
Still ~1.6–2MB release (no ML Kit natives).

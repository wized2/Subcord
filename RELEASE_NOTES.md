## Subcord 1.1.1 — translation reliability

### Fixes
- **Debounced scanning** instead of cancelling every accessibility event (this was dropping translations mid-request)
- **Rescan** when overlay starts so already-visible messages are translated
- **MyMemory fallback** if Google Translate endpoint is blocked
- Better Arabic / RTL message detection and overlay text
- Faster accessibility notification timeout

### Size
Still ~1.6MB release (no ML Kit natives).

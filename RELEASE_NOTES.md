## Subcord 1.3.1 — readable translations + cleaner Arabic input

### Visibility
- Translation cards use **high-contrast** colors for light and dark Discord themes (no more washed-out text)

### Quality
- Strip `(edited)`, timestamps, and date lines before translating
- Detect Arabic and request `sl=ar` (better than pure auto for dialect noise)
- Prefer innermost message node only (less username / reply junk)

Machine translation of informal dialect Arabic will still be imperfect — free APIs are not human quality — but results should be clearer and less garbage-filled.

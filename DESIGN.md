# Vexel Launcher design system

One rulebook for every glyph and surface. The values live in `Design` (`Ui.kt`); the glyphs in `Glyphs.kt`.

## Rules

| Rule | Value |
| --- | --- |
| Grid | 24 x 24 units, glyphs drawn inside it |
| Stroke | 2 units, one weight everywhere |
| Caps and joins | Round |
| Style | Outlined. Filled is reserved for active states (island play / pause / skip) |
| Tile | Frosted white glass, corner radius 20% of the tile, glyph box 52% of the tile |
| Ink | Navy-slate gradient (`#1E2234` to `#585E76`) with a soft 1dp shadow |
| Backdrop | Purple-black gradient (`Backdrop` in `Ui.kt`), two drifting violet glows |
| Surfaces | Glass: milky white gradient, 1-2px light rim (`glass`, `glassDock`) |
| Island | Pure black pill so it stands off the purple |

## Metaphors

Chosen by what the app does, not by its logo. Brand marks are used only where the brand *is* the metaphor
(Instagram, Claude, WhatsApp). Anything unmapped falls back to `apps` (four squares).

| Family | Glyph | Apps |
| --- | --- | --- |
| Communication | phone, message, chat, mail, user, users | Phone, Messages, WhatsApp / WA Business, Gmail, Contacts, Teams |
| Capture and media | camera, insta, image, video, music, headphones | Camera, Instagram, Gallery / Photos, YouTube, TikTok, Spotify |
| Navigation and travel | pin, car, bolt, cart | Maps, Little, Bolt, MAFCarrefour |
| Files and tools | folder, file, download, note, calc, calendar, clock | Files, Downloads, Notes, Calculator, Calendar, Clock |
| System | sliders, shield, lock, layers, wifi, droplet, remote, help | Settings, Security, Authenticator, App vault, SmartAccess, Themes, Mi Remote, Services & feedback |
| Money and store | bag, card, sim | Play Store, LOOP, Safaricom |
| Weather | sun, cloud, rain, snow, storm, fog | Weather chip on the home page |
| Other | spark, gamepad, briefcase, globe | Claude, games, LinkedIn, Chrome |

## Naming

Glyph names are lowercase nouns (`download`, `sliders`), never app names, so one glyph can serve several apps.
`Glyphs.forApp(label, package)` is the only place apps are mapped to glyphs.

## Adding a glyph

1. Draw it on the 24-unit grid with the shared stroke; use `path`, `circle`, `rect`, `line`, `dot`.
2. Add a name to `glyphs` in `Glyphs.kt` and a rule to `forApp`.
3. Check it on the app page at tile size and beside weather-chip size (22dp).

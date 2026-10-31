# Vexel Launcher design system

One rulebook for every glyph and surface. The numbers live in `Design` (`Ui.kt`); the glyphs in `Glyphs.kt`.
This describes version 1.2.

## Rules

| Rule | Value |
| --- | --- |
| Grid | 24 x 24 units; every glyph is drawn inside it |
| Stroke | 2 units, one weight everywhere |
| Caps and joins | Round |
| Style | Outlined. Filled shapes are reserved for active states: play, pause, previous and next in the island |
| Tile | Frosted white-lavender glass, 56dp, corner radius 28% of the tile (`Design.TILE_RADIUS`) |
| Tile colour | Near-white `#FCF8FF` fading to lavender `#D6C2FA`, a violet glow along the base, a bright sheen across the top, a white rim |
| Glyph box | 52% of the tile (`Design.GLYPH_BOX`), centred |
| Ink | Deep purple `#341270` fading to `#764CC4`, with a soft 1dp violet shadow |
| Brand apps | Same outlined style, with the glyph drawn from the app's own logo shape: Spotify's arcs, WhatsApp's bubble and handset, TikTok's note, LinkedIn's "in" |
| Backdrop | Black base with three royal-purple glows (`#5B21B6`, `#7C3AED`, `#431490`) drifting on slow orbits; animates only while the launcher is visible |
| Surfaces | Violet-tinted glass with a lavender rim: the weather chip (`glass`) and the dock (`glassDock`, corner radius 32dp) |
| Island | Pure black pill (36dp compact, rounded to a pill; 34dp radius expanded) so it stands off the purple |
| Navigation bar | Black, system height; white 2-2.4dp strokes: three bars, a 17dp rounded square, a chevron; four equal gaps between the shapes and the screen edges |

## Type

Inter (SIL OFL): Medium 80sp for the home clock (104sp on the lock screen), Regular for dates, labels and body
text, Bold for titles and chips. Labels are 11sp under the tiles.

## Layout

- One page. Header (time, date, weather) fixed at the top; the app grid below it, five columns, anchored to the
  bottom and scrolling under soft edges when there are more than 35 apps; the favourites dock above the
  system bar.
- The dock holds up to five apps; docked apps are not repeated in the grid.
- Spacing follows an 8dp rhythm: 56dp tiles and transport targets, 16dp gaps in the island, 24dp page padding.

## Metaphors

Chosen by what an app does, not by its logo. Brand shapes are used only where the brand *is* the metaphor.
Anything unmapped falls back to `apps` (four squares).

| Family | Glyph | Apps |
| --- | --- | --- |
| Communication | phone, message, whatsapp, wabusiness, mail, user, teams | Phone, Messages, WhatsApp, WA Business, Gmail, Contacts, Teams |
| Capture and media | camera, insta, image, video, spotify, tiktok, music | Camera, Instagram, Photos, YouTube, Spotify, TikTok |
| Navigation and travel | pin, car, bolt, bike | Maps, Little, Bolt, Mountain Bike Xtreme |
| Shopping and money | cart, card, bag, sim, healthshield | MAFCarrefour, LOOP, Play Store, Safaricom, SmartAccess |
| Files and tools | folder, file, download, note, calc, calendar, clock | Files, Notes, Calculator, Calendar, Clock |
| System | sliders, shield, lock, layers, remote, help, droplet, wifi | Settings, Security, Authenticator, Mi Remote |
| Work | briefcase, linkedin | LinkedIn |
| Weather | sun, cloud, rain, snow, storm, fog | Weather chip on the home page |
| Controls | play, pause, next, prev, flashlight | Island transport controls, lock screen torch |
| Other | spark, gamepad, globe, apps | Claude, games, Chrome, anything unmapped |

## Naming

Glyph names are lowercase nouns (`download`, `sliders`), never app names, so one glyph can serve several apps.
`Glyphs.forApp(label, package)` is the only place apps are mapped to glyphs.

## Adding a glyph

1. Draw it on the 24-unit grid with the shared stroke; use `path`, `circle`, `rect`, `line`, `dot`.
   Filled shapes use `solidPath` or `solidRect` and must be wound clockwise: Skia leaves a hole in a
   counter-clockwise fill-and-stroke path.
2. Add a name to `glyphs` in `Glyphs.kt` and a rule to `forApp`. Put more specific rules before general ones
   (for example `wa business` before `whatsapp`).
3. Check it on the app page at tile size, and beside the weather chip at 22dp if it appears there.

## Known gaps

- Glyphs have not been checked at 16px, and there is no SVG or PNG export of the set.
- The lock screen, the island's alarm, call and ride views, and touch scrolling beyond 35 apps are built but not
  yet exercised on a device.

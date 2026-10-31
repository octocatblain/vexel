## Vexel Launcher 1.2

A black and royal-purple home screen for Android 12+ (built and tested on a Xiaomi phone running HyperOS).

### Highlights

- **One page.** Clock, date and weather up top, every app in a five-column grid that hugs the bottom, favourites
  docked underneath. Docked apps aren't repeated in the grid. Tap the time, date or weather to open Clock,
  Calendar or Weather.
- **Glass icons.** Frosted white-lavender tiles with deep-purple outlined glyphs, drawn from one 24-unit grid with
  one stroke weight, and logo-shaped glyphs for Spotify, WhatsApp, TikTok and LinkedIn.
- **Moving backdrop.** Three royal-purple glows drift over black, only while the launcher is on screen.
- **Dynamic Island.** Shows what's playing with art, progress and controls, and alarms, calls, timers and ride
  notifications. Visible while the launcher is on screen.
- **Samsung-style navigation bar.** Recents, home and back with four equal gaps and a square home button, over the
  system buttons in every app. It switches itself back on after you clear apps.
- **Inter** for all text.

### Install

Download `vexel-launcher.apk`, open it, allow installs from your browser or file manager, and choose
*More details → Install anyway* if Google Play Protect hasn't seen the app. Then press Home and pick
Vexel Launcher as your home app. The island, the self-healing navigation bar and the lock screen need a one-time
`adb` setup: see the README.

### Check the download

```
SHA-256  26137126c53c929a3c241909b807e93d06378827de6ddb1c4eb43e8b0a42cd08
```

### Known limits

- The island appears only while the launcher is on screen.
- The custom lock screen is built but has not been verified on a device.
- Android gives ordinary apps only the next alarm, so the home screen does not list upcoming alarms.
- Xiaomi's gesture navigation doesn't work with a third-party home app; the phone stays on three buttons,
  drawn by Vexel.
- Debug-signed copies must be uninstalled before this release-signed one installs.

# Vexel Launcher

A custom Android home screen built for a Xiaomi phone (HyperOS / Android 15). Black and royal-purple
background in slow motion, frosted-glass app tiles with a purple outlined icon set, an iPhone-style
Dynamic Island, and a Samsung-style navigation bar.

- Package: `com.vexel.launcher`
- Version: 1.2 (version code 3)
- Min SDK 31, target SDK 35, Kotlin, no third-party UI libraries (AndroidX core only)

## What it does

| Feature | Notes |
| --- | --- |
| One-page home | Clock, date and weather at the top; every app in a five-column grid that hugs the bottom; favourites docked underneath |
| Favourites dock | Phone, Messages, Spotify, WhatsApp, WA Business by default. Long-press any app to add or remove (up to five). Docked apps are not repeated in the grid |
| Icon set | One 24-unit grid, one 2-unit stroke, round caps, outlined only. Apps are mapped to glyphs by metaphor, with brand-shaped glyphs for Spotify, WhatsApp, TikTok, LinkedIn and others. See [DESIGN.md](DESIGN.md) |
| Moving backdrop | Three royal-purple glows drift over black. Animates only while the launcher is visible |
| Dynamic Island | A black pill under the camera. Shows Spotify (or any media) with art, progress and controls, plus alarms, calls, timers, navigation and ride apps such as Bolt. Tap to expand, swipe to switch between activities. Visible only while the launcher is on screen |
| Weather | Open-Meteo at your coarse location, no API key |
| Shade gesture | Swipe down from the clock, date or weather area to open the notification shade |
| Navigation bar | A Samsung-style bar (recents, home, back) drawn over the system buttons in every app. See below |
| Lock screen | A custom lock screen drawn over the system keyguard. Built, **not yet verified on the device** |
| Font | Inter (SIL OFL), the open-source face closest to Apple's San Francisco: Medium for clocks, Regular for text, Bold for titles |

## Build

Requires the Android SDK and the JDK that ships with Android Studio.

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat assembleDebug      # debug-signed
gradlew.bat assembleRelease    # signed with signing/vexel-release.jks
```

Outputs are in `app/build/outputs/apk/{debug,release}/`.

The project uses Android Gradle Plugin 9.0.0 and Gradle 9.6.0, because Android Studio's bundled JDK is
version 25 and older Gradle versions cannot read it. Kotlin comes from the AGP built-in support.

### Signing

Release builds are signed with `signing/vexel-release.jks`, configured through
`signing/keystore.properties`. Both are git-ignored. **Back them up**: an update can only be installed over
an existing copy if it is signed with the same key. A copy signed with a different key (such as the debug
key) has to be uninstalled first.

## Install and one-time phone setup

Turn on USB debugging, then:

```
adb install -r app/build/outputs/apk/release/app-release.apk

adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.vexel.launcher   # default home app
adb shell cmd notification allow_listener com.vexel.launcher/com.vexel.launcher.IslandListenerService
adb shell pm grant com.vexel.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell dumpsys deviceidle whitelist +com.vexel.launcher                                # battery limits
```

Then open the launcher once. It re-enables its own navigation service (see below). Weather asks for
location permission the first time.

If you use the lock screen, also grant notifications and full-screen notifications:

```
adb shell pm grant com.vexel.launcher android.permission.POST_NOTIFICATIONS
adb shell cmd appops set com.vexel.launcher USE_FULL_SCREEN_INTENT allow
```

### Xiaomi quirks

- **USB installs.** Xiaomi blocks them intermittently with `INSTALL_FAILED_USER_RESTRICTED`. Turn **Install via
  USB** off and on in Developer options, keep the screen on and unlocked, or install the APK from a file manager.
- **`adb push` from Git Bash** rewrites `/sdcard/...` into a Windows path and silently saves nothing. Use
  `MSYS_NO_PATHCONV=1` or PowerShell.
- **Default home app.** Reinstalling can reset it to Xiaomi's launcher. Re-run the `add-role-holder` command.

## Navigation bar

`NavBarService` is an accessibility service. It draws a black bar over the system navigation buttons at the
same height and presses the real recents, home and back actions. It never reads the screen. The three buttons
sit in equal gaps (screen edge, recents, home, back, screen edge) and each takes a third of the bar for touch.

- Turn it off in Settings → Accessibility → Installed apps → Vexel navigation bar. Xiaomi's own buttons return.
- Clearing apps force-stops Vexel, which switches the service off. Because Vexel is the home app it restarts at
  once and re-enables the service in `MainActivity.ensureNavBarEnabled()`. This needs the
  `WRITE_SECURE_SETTINGS` grant above; without it the bar must be re-enabled by hand.
- The bar hides on the lock screen. It may stay visible over full-screen apps and videos.

### Why not gestures

Xiaomi's full-screen gestures run through Xiaomi's own launcher, so with Vexel as the home app they show the
bar but don't respond. Android's standard gesture overlay didn't work on this phone either. The phone is
therefore on three-button navigation (`navigation_mode` 0), replaced visually by the bar above.

## Lock screen

`LockService` runs as a foreground service. When the screen turns on while locked it posts a full-screen
notification that opens `LockScreenActivity` over the keyguard: clock, weather, island, torch, camera, and
swipe up to unlock. Unlocking asks the system to dismiss the keyguard, so a PIN or fingerprint is still
required. It cannot replace the real lock screen, and Xiaomi may need Autostart and background pop-ups allowed
for it to appear. Turn it off by stopping the service or clearing the `lockScreen` preference.

## Project layout

```
app/src/main/java/com/vexel/launcher/
  MainActivity.kt          home screen, dock, grid, weather, gestures
  Glyphs.kt                the icon set and the app-to-glyph mapping
  Ui.kt                    design tokens, glass drawables, animated Backdrop
  Fonts.kt                 Inter weights
  IslandView.kt            the Dynamic Island view
  IslandRepository.kt      media sessions and notifications feeding the island
  IslandListenerService.kt notification access
  NavBarService.kt         Samsung-style navigation bar
  LockService.kt           lock screen trigger
  LockScreenActivity.kt    the lock screen
  BootReceiver.kt          restarts the lock service after boot
  Weather.kt               Open-Meteo client
app/src/main/res/font/     Inter Medium, Regular, Bold
licenses/Inter-OFL.txt     font licence
DESIGN.md                  design rules and metaphor map
```

## Known limits

- The island appears only while the launcher is on screen, not over other apps.
- Alarms, calls, timers and ride notifications are wired up but have not been exercised on the device. Spotify
  has.
- The lock screen has not been verified on the device.
- Removed apps (Gemini, Game Center, Mi Video, Gallery, Downloads, Themes, App vault, Services & feedback and
  others) were removed for the user only and can be restored with
  `adb shell cmd package install-existing <package>`.
- Google Keep still has to be installed from the Play Store, and notes cannot be imported into it
  automatically.

## Credits

Inter by Rasmus Andersson, under the SIL Open Font License 1.1 (`licenses/Inter-OFL.txt`). Weather data from
Open-Meteo.

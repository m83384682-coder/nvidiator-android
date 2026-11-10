<img src="icon-512.png" alt="" width="96" align="right">

# Nvidiator Android

**An unofficial, experimental port of NVDA's sound and soul to Android.**

> **Experimental and unofficial.** Nvidiator is not made, endorsed or supported by NV Access, and it is not NVDA for Android. It is a hobby project that takes NVDA's own Python modules, sounds, wording and translations and runs them inside an Android screen reader. Expect rough edges. It works on the author's phone, but different phones may give different results. Read [Getting back to TalkBack](#getting-back-to-talkback) before you turn it on.

Nvidiator is an Android accessibility service (a screen reader, like TalkBack) written in Java. The parts that make NVDA sound like NVDA run as real NVDA Python code on the phone, through [Chaquopy](https://chaquo.com/chaquopy/):

- **NVDA's sounds and beeps**: the start and exit sounds, errors, browse and focus mode, the progress-bar and input-help tones.
- **NVDA's wording**: roles, states and messages come from NVDA's own `controlTypes` and `characterProcessing`, in NVDA's own translations (73 languages, compiled from NVDA's `nvda.po` files). Punctuation and symbol levels use NVDA's `symbols.dic`, and spelling uses NVDA's character descriptions.
- **NVDA's menus and screens**: the NVDA menu, the Exit dialog, the welcome dialog, Settings (in NVDA's categories), Input gestures, the Add-on Store, the Speech viewer and the Python console use NVDA's exact text, translated by NVDA's translators. In German, for example, they read "Einstellungen…", "Beenden" and "Sprachausgabe-Betrachter". The only additions are a short gesture or key hint under menu items, notes where a setting works differently on Android, a tutorial and a quick reference.
- **NVDA's Python console**: NVDA's own console code, running on the phone. See [The Python console](#the-python-console).
- **NVDA add-ons**: some NVDA add-ons run, including focus-based sound packs. See [Add-ons](#add-ons).
- **NVDA's commands**: touch gestures follow NVDA's touch-screen commands, and a Bluetooth or USB keyboard gets NVDA's keyboard commands with Insert or Caps Lock as the NVDA key, plus single letter navigation.

Speech comes from any Android text-to-speech engine you have installed (the phone's default, or one you choose in Settings).

## Status

Version 0.2, still experimental. It works on the author's phone, but different phones may give different results.

### What's new in 0.2

- **Add-ons**: install NVDA add-on packages (`.nvda-addon`) from the Add-on Store's "Install from external source", and turn them on or off, or remove them. See [Add-ons](#add-ons).
- **Input gestures**: NVDA's Input gestures screen for touch gestures. Change what any gesture does; the NVDA menu always keeps at least one gesture.
- **Welcome dialog**: NVDA's welcome dialog on first run, with its "Show this dialog when NVDA starts" checkbox.
- **Settings in NVDA's categories**: choose a category, then Back returns to the list.
- **Bigger Python console**: a taller code box, with the buttons in two rows that stay on screen when the keyboard is open.
- **Speech interrupt on double tap**: a double tap, or a double tap and hold, stops speech at once, and the label under your finger isn't read again while you hold (this fixes long messages talking over you in WhatsApp). Swipes and touch exploring don't trigger it.
- **Automatic scrolling**: swiping past the last visible item scrolls the list and moves to the first new item.
- **Lock screen**: says "Screen off", "Screen locked" and "Unlocked". Typing a PIN gives one star per key press, and the star never delays, drops or repeats a key press.
- **New commands**: clear the edit box, paste, copy, cut, select all, scroll forward and back, read the clipboard (NVDA+C), sleep mode for the current app (NVDA+Shift+S), toggles for automatic scrolling, speech interrupt and say cap. Reporting the current object twice quickly spells it, as in NVDA.
- **Capital letters**: NVDA's capitals pitch change, "cap" and beep.
- **Speech interrupt for typed characters**, a rate boost, and a volume setting for NVDA's sounds.
- **More NVDA sounds**: copy, paste, and connected and disconnected (headphones, Bluetooth audio, the charger).

**Built and checked on a computer:**

- The APKs (64-bit, 32-bit and universal) pass Android's signature and zip-alignment checks, including 16 KB page alignment. They use the same package name and key as 0.1, so 0.2 installs over it.
- The Python side passes its desktop tests (`tests/`): the NVDA bridge, the Python console, add-ons, and a sample sound-pack add-on.

**Not tested on a phone yet:** the new screens, add-ons on a device, the speech interrupt, automatic scrolling, lock screen and PIN speech, read clipboard and sleep mode.

**Known gaps:**

- Browse mode is simplified. Android has no virtual buffer like a web browser on Windows, so single letter navigation moves through what Android reports on screen.
- No braille, no NVDA synthesizers (eSpeak, OneCore). Speech uses Android's text-to-speech engines.
- No speech dictionaries yet (planned for 0.3).
- Sleep mode keeps Nvidiator quiet in that app, but Android can't hand gestures to the app while a screen reader is on, so gestures still go to Nvidiator.
- Review cursor commands are limited to characters, words and lines of the current object.
- The APKs are signed with a debug key, so Play Protect may warn you.

Please report problems in [Issues](../../issues).

## Install

Nvidiator needs Android 7.0 or newer. Download the APK for your phone from the [latest release](../../releases):

| File | For |
|---|---|
| `nvidiator-android-0.2-arm64-v8a.apk` | Most phones from the last several years (64-bit) |
| `nvidiator-android-0.2-armeabi-v7a.apk` | Older 32-bit phones |
| `nvidiator-android-0.2-universal.apk` | Any phone or emulator, if you are not sure (bigger) |

Then:

1. Install it, by tapping the file or with `adb install nvidiator-android-0.2-arm64-v8a.apk`. Android may ask you to allow installs from unknown sources.
2. Open Nvidiator once and read the safety notes.
3. Optional but recommended: give it the permission to switch straight back to TalkBack (see below).
4. Make sure the accessibility shortcut (hold both volume keys) is set to TalkBack, in Settings, Accessibility.
5. Turn TalkBack off and Nvidiator on, in Settings, Accessibility, Installed apps (or Downloaded apps), Nvidiator. If TalkBack is on and the permission is granted, the app's "Switch from TalkBack to Nvidiator" button does this in one step.

## Getting back to TalkBack

There are three ways out, and the last two always work:

1. **The Exit menu.** Open the NVDA menu (swipe down then right, tap with three fingers, double tap with two fingers, or NVDA+N) and choose Exit, or press NVDA+Q twice. NVDA's Exit dialog offers **Exit to TalkBack**, which turns Nvidiator off and TalkBack on in one step. That needs a permission you grant once from a computer:

   ```
   adb shell pm grant com.irondonkey.nvidiator android.permission.WRITE_SECURE_SETTINGS
   ```

   Without it, Exit still turns Nvidiator off, and you turn TalkBack on with the shortcut below.
2. **The accessibility shortcut.** Hold both volume keys for about 3 seconds. Nvidiator never takes over the volume keys, so this always reaches Android. Set the shortcut to TalkBack before you try Nvidiator.
3. **The 15-second safety switch.** If no speech starts within 15 seconds of turning Nvidiator on, it plays NVDA's error sound and turns itself off, so touch works normally again.

From a computer you can also run `adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService` to put TalkBack back.

## Touch gestures

They follow NVDA's touch commands, with TalkBack's gestures for Android's own buttons.

| Gesture | Action |
|---|---|
| Slide one finger | Explore by touch |
| Double tap | Activate |
| Swipe right / left | Next / previous object (text mode: character) |
| Swipe up | Object containing it (text mode: previous line) |
| Swipe down | First object inside it (text mode: next line) |
| Three-finger swipe right / left | Next / previous word |
| Three-finger or four-finger swipe up | Report focus |
| Four-finger swipe down | Report the current object (twice quickly: spell it) |
| Three-finger swipe down, or swipe down then up | Say all |
| Two-finger tap | Stop speech |
| Swipe down then right, three-finger tap, or two-finger double tap | NVDA menu |
| Swipe up then down, or three-finger double tap | Switch object mode and text mode |
| Swipe left then right | In an edit box: clear it. Elsewhere: report the current object |
| Swipe right then left | In an edit box: paste. Elsewhere: move to the focus |
| Swipe left then down | Report title |
| Swipe right then up | Time (twice quickly: date) |
| Swipe up then right | Speech viewer |
| Swipe down then left | Back |
| Swipe up then left | Home |
| Swipe left then up | Recent apps |
| Swipe right then down | Notifications |
| Three-finger triple tap | Speech mode |
| Two-finger triple tap | Punctuation and symbol level |
| Four-finger tap | Input help |
| Four-finger double tap | Python console |
| Hold both volume keys | Accessibility shortcut (always works) |

You can change any of these in the NVDA menu, Preferences, Input gestures.

## Keyboard commands

With a Bluetooth or USB keyboard. The NVDA key is Insert or Caps Lock (both can be changed in Settings); Numpad 0 also works when Insert is on.

| Keys | Action |
|---|---|
| NVDA+N | NVDA menu |
| NVDA+Q twice | Exit Nvidiator |
| NVDA+Down arrow or NVDA+A | Say all |
| NVDA+Up arrow or NVDA+L | Report the current object (twice quickly: spell it) |
| NVDA+Tab | Report focus |
| NVDA+T | Report title |
| NVDA+B | Read the active window |
| NVDA+Enter, NVDA+Numpad star | Activate |
| NVDA+Space | Switch browse mode and focus mode |
| NVDA+S | Speech mode |
| NVDA+P | Punctuation and symbol level |
| NVDA+1 | Input help |
| NVDA+2 / NVDA+3 | Speak typed characters / words |
| NVDA+C | Read the clipboard |
| NVDA+Shift+S | Sleep mode for the current app |
| NVDA+F12 | Time (twice quickly: date) |
| NVDA+F1 | Speech viewer |
| NVDA+Shift+B | Battery status |
| NVDA+Shift+arrows | Object navigation (next, previous, containing, first inside) |
| NVDA+Numpad 6, 4, 8, 2, 5 | Object navigation and current object |
| NVDA+Ctrl+arrows | Synth settings ring (rate, pitch, volume) |
| NVDA+Ctrl+G | NVDA Settings |
| NVDA+Ctrl+Z | Python console |
| Control | Stop speech |

**Single letter navigation** (add Shift to go back; off in edit fields and in focus mode): H heading, K link, B button, E edit field, F form field, X check box or switch, C combo box, R radio button, L list, I list item, G graphic, D landmark, T table.

## The Python console

NVDA's own `pythonConsole.py`, adapted to run on Android. Open it with a four-finger double tap, NVDA+Ctrl+Z, or the NVDA menu (Tools).

- Code you type or paste runs as real Python 3.11 on the phone. One line runs like NVDA's prompt (`>>>` and `...`); several pasted lines run together as one script.
- Tab completion, `help()`, `exit()` and NVDA's snapshot variables: `focus`, `nav`, `fg` and `caretObj` are the actual on-screen items.
- `speech`, `tones`, `ui` and `api` control the running screen reader, for example `speech.speakMessage("hello")`, `tones.beep(440, 100)` or `ui.message("hi")`. NVDA's modules such as `controlTypes` are there too, and `jclass` reaches Android's Java classes.
- Like NVDA's console, `_` holds the last result.

The console runs code with the screen reader's own permissions. Only paste code you trust.

## Add-ons

Open the NVDA menu, Tools, Add-on Store, then "Install from external source" and choose a `.nvda-addon` file. There is no online catalogue. Add-ons run as real Python on the phone, with Android stand-ins for the NVDA parts they use most.

**What works:**

- **Global plugins** (`globalPlugins/`) with NVDA-style scripts and gestures, translations, and `__gestures` bound to touch gestures.
- **Events**: `event_gainFocus`, `event_loseFocus`, `event_becomeNavigatorObject`, `event_foreground`, `event_stateChange`, `event_valueChange` and `event_nameChange`, with `nextHandler`. The object passed in has NVDA's `role`, `states`, `name`, `value`, `description`, `positionInfo`, `windowClassName` and `appModule.appName` (the Android app's package name). On Android, moving to an item with a swipe or by touch counts as a focus change.
- **Speech and sounds**: `speech.speak` and `ui.message`, the speech extension points `pre_speech` and `filter_speechSequence` (they see everything Nvidiator says), `WaveFileCommand` and `BeepCommand` in speech sequences, `nvwave.playWaveFile` for NVDA's sounds or `.wav` files shipped in the add-on, `nvwave.decide_playWaveFile` and `tones.beep` with `tones.decide_beep`.
- **Sound packs** that play a sound for each role or state on focus (earcon or role-sound style add-ons) have a real chance of working, as long as they only use the parts above.

**What doesn't:**

- Anything that needs Windows: `winUser`, `ctypes` Windows calls, IAccessible, UI Automation, `NVDAObjects` overlay classes, app modules for Windows programs, synthesizer and braille drivers, and add-ons that add their own GUI with `wx`.
- Browse mode and virtual buffer add-ons.

An add-on that fails to load, or whose script or event handler crashes, is turned off, and Nvidiator says why in plain words. The screen reader keeps running.

## Building from source

You need JDK 17 or newer, the Android SDK (platform 36, build tools 36.x) and Python 3.11 on your PATH (Chaquopy uses it to compile the Python code; set `python.build` in `local.properties` if it is somewhere else).

```
./gradlew assembleDebug
```

The APKs land in `build/outputs/apk/debug/`. Desktop tests for the Python side: `python3.11 tests/test_bridge.py`, `tests/test_console.py`, `tests/test_addons.py` and `tests/test_soundpack.py`.

NVDA's translations and dictionaries in `src/main/assets/nvda/locale` were made from NVDA's source with `tools/build_locale_data.py` (needs `pip install polib`). The NVDA Python modules in `src/main/python` come from NVDA's `source/` folder, with Windows-only parts replaced by Android code. The NVDA source used is [nvaccess/nvda](https://github.com/nvaccess/nvda) at commit `af55fb6b`.

## Why "Nvidiator"?

It is NVDA, trying its best to be a gladiator in an arena it was never built for.

## Credits and licence

- **NVDA** (NonVisual Desktop Access) is made by [NV Access Limited](https://www.nvaccess.org/) and contributors. Nvidiator uses NVDA's Python modules, sounds, wording and translations. NVDA's translations are the work of NVDA's volunteer translators.
- **Chaquopy** runs Python on Android.
- Built by Iron Donkey.

Nvidiator is free software under the **GNU General Public License version 2**, the same licence as NVDA. See [LICENSE](LICENSE). It comes with no warranty.

NVDA and NV Access are names of NV Access Limited. Nvidiator is not affiliated with, endorsed by or supported by NV Access. Please do not report Nvidiator problems to NV Access.

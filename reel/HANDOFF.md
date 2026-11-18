# Reel — handoff

Reel is a mobile video editor for Android, in the style of CapCut and VN: a timeline with clips,
text, stickers, shapes, overlays and audio. It has hundreds of effects and transitions, on-device
captions, background cutout, head tracking and in-app updates. Current version: **1.9.0**
(versionCode 16).

This document is for whoever picks the project up next: how to build it, where things live, how it
is tested and released, what is known to be weak, and what is still to do.

---

## 1. Build

| | |
|---|---|
| Language | Kotlin 2.2.21, C++17 (NDK 27.2.12479018) |
| UI | Jetpack Compose (BOM 2025.12.01), Material 3 |
| Video | Media3 1.11.1 (`CompositionPlayer` for preview, `Transformer` for export) |
| Gradle | AGP 8.13.2, Gradle 8.14 (`gradle` on PATH; there is no wrapper jar) |
| SDK | compile/target 36, min 26 |
| ABIs | arm64-v8a, armeabi-v7a, x86_64 |

```bash
cd reel
tools/fetch_model.sh                 # 264 MB speech model, not in git (checksum-verified)
gradle :app:assembleDebug            # debug build
gradle :app:testDebugUnitTest        # 81 JVM tests (a few call python3 + Pillow if present)
gradle :app:assembleRelease          # needs signing.properties + the keystore (see below)
```

Files that are **not in git** on purpose:

- `app/src/main/assets/models/speech.bin`: the captions model. Run `tools/fetch_model.sh`.
- `signing.properties` and `reel-release.jks`: the release signing key. Every update must be signed
  with this same key, or Android refuses it over the installed app. The owner holds the only copies
  (sent to them directly, never committed). Put both files in `reel/` to build releases. If the key
  is lost, users must uninstall and reinstall.
- `local.properties`: points at your Android SDK.

The APK is about 300 MB, mostly the speech model. The model is bundled on purpose: the owner wants
captions to work offline with no download.

The release build uses R8 with `-dontobfuscate`, so Sentry stack traces are readable. Sentry
(`io.sentry:sentry-android`) runs only in release builds; its DSN is in `app/build.gradle.kts`.

---

## 2. Product rules (from the owner)

- No explanation text under labels. Icons wherever possible, uncluttered screens.
- No em dashes in UI text.
- The UI must never mention "whisper". The speech model is just "captions".
- Use the bundled small model only; no model picker, no downloads.
- The design reference is the "Reel Video Editor" canvas artifact the owner made; screens follow it.

---

## 3. Layout of the code

```
app/src/main/java/com/reel/editor/
  model/       Project data (Project.kt), timeline maths (Timeline.kt), lanes (TrackLayout.kt),
               captions grouping + anchoring + line edits (Captions.kt), head path maths (HeadPath.kt),
               beats, voice presets, media-uri remapping for project files (ProjectUris.kt)
  edit/        EditorViewModel (all editing actions), History (undo/redo + named steps),
               ProjectStore (JSON drafts), ProjectArchive (.reelproj share/import)
  engine/      CompositionFactory (project -> Media3 Composition), PreviewController,
               Exporter (+ GIF and sound-only), ExportService, audio processors (volume, voice)
  fx/          GL rendering: ClipRenderer (per-clip pass pipeline), RenderPlan, OverlayRenderer,
               Layers (text/sticker/shape bitmaps + text animations), FxCatalog (shader library),
               Segmenter (person mask), UserLuts (.cube import), FxThumbs (effect previews)
  media/       MediaLibrary (in-app picker data), probe, thumbnails, audio decode/analysis,
               Reverser, GifWriter, Speech (text to speech)
  track/       HeadTracker (face detector + person-mask fallback), FrameSampler (one-pass decode)
  captions/    CaptionEngine (on-device transcription through JNI)
  update/      Updater (in-app updates with binary patches)
  ui/          Compose screens and panels (EditorScreen, Timeline, Panels*, MediaPicker, ...)
app/src/main/cpp/  JNI core: looks (LUT generator), audio analysis, voice DSP, image ops,
               captions core; third_party/whisper.cpp vendored (v1.9.5, CPU only)
app/src/main/assets/fx/  GLSL library: 307 transitions (211 from gl-transitions, MIT, plus 96 of
               our own), 145 filters, 157 effects, 54 clip animations, internal shaders (canvas,
               grade, overlay_blend, privacy, ...)
tools/fx/      shader sources and tools: library.glsl (filters, effects, clip animations; built by
               build_library.py), reel_transitions.glsl (our transitions; build_transitions.py),
               validate_shaders.py (glslangValidator), render_check.py (renders everything)
tools/update/  make_update.py (update patches) + README
```

### How a frame is drawn

`ClipRenderer` is a Media3 `GlEffect` attached to every clip. For each frame it runs:

1. **Pre-canvas, at working size:** grade (adjustments, HSL, curves, look LUT), the clip's filter
   and its effects.
2. **Canvas:** fit or fill, crop, keyframed transform, flip, chroma key, cutout, clip mask and
   background. Then the hidden region (face blur), in/out animations and the transition.
3. **Tail capture** for the next clip's transition.
4. **Global passes:** overlays (with blend, mask and chroma), timed looks and filters, adjustment
   layers, timed effects.
5. **Final:** text, stickers and shapes. Captions are drawn last, so they sit on top.

Effect previews in the pickers use two bundled pictures (`assets/samples`, credits in
`CREDITS.txt`): a CC0 portrait for filters, looks and clip animations, and a public-domain NASA photo
of an apple floating above Earth for effects. Transitions go from one to the other.

The preview and the export share this path. Only the preview honours "hold to see the original".

### Data model notes

- Everything is an immutable `@Serializable` data class. Edits go through
  `EditorViewModel.edit/live`, which run `CaptionAnchor.reflow`, then `TrackLayout.clampLayers`,
  then save.
- Captions remember the source clip and media time they came from (`anchor*` fields). When clips
  are trimmed, split or moved, the captions follow; captions whose speech was cut are removed.
- Track switches live in `Project.tracks`. They work like a desktop editor's: a hidden track stays
  on screen at 35% opacity while paused (`RenderPlans.build(ghostHidden = true)`, pushed by
  `PreviewController` on every play/pause), and it is left out of playback and export
  (`RenderPlans.visible`). Hiding never deselects; only locking does. Each timeline row starts with
  an icon for its type. Video: eye, sound, lock. Overlays: eye, sound (if any), lock. Text and
  stickers: eye, lock. Effects: an fx on/off switch, lock. Music and voice: sound, lock, no eye.

---

## 4. Features (all shipped and tested unless noted)

- **Editing:** trim, split, reorder, speed and speed curves, freeze, replace, duplicate, reverse,
  crop, rotate, flip, extract audio, transitions, remove pauses (`model/Voice.kt` `Pauses`: cuts
  quiet stretches longer than 450 ms out of a talking clip, keeping 110 ms around the words).
- **Selection:** multi-select, copy and paste style, undo and redo with a history list.
- **Text templates:** 34 ready-made designs in 7 categories (titles, lower thirds, social, quotes,
  neon, retro, moments), built from text plus shapes and staggered animations
  (`model/TextTemplates.kt`), with live rendered tiles; one-tap style presets on any text.
- **Layers:** text (61 bundled fonts, gradient, curved text, 30+ animations including
  letter-by-letter), stickers, 12 shapes, overlays (picture in picture with 9 blend modes, masks
  and chroma key), adjustment layers.
- **Look:** 100+ looks, `.cube` LUT import, adjustments, curves, HSL, filters, effects, clip masks,
  chroma key, background cutout, and Auto enhance (`model/AutoEnhance.kt`: exposure, contrast,
  grey-world white balance and vibrance from 5 frames, solved against the grade shader's maths).
- **Motion:** keyframes on everything, a value graph, a bezier easing editor, head tracking
  (layers follow a head), auto reframe, face blur that follows the face, and stabilization
  (`model/Stabilize.kt`, `track/Stabilizer.kt`: block matching at 160 px with a coarse whole-frame
  pass, a robust shift-and-turn fit, Gaussian smoothing of the camera path, then clip keyframes
  that cancel the shake with the least zoom that hides the edges; Light, Normal, Strong), and
  frame interpolation ("Smooth" in Speed, plus a Smooth slow-mo preset): `cpp/interp.cpp`
  estimates motion with coarse-to-fine block matching on a 320 px grey copy (median and blur on
  the vector grid), then each in-between frame pulls from both neighbours along the motion and
  blends, letting the nearer frame win where they disagree. `media/Smoother.kt` writes a copy at
  about 120 fps (30 fps x4, 24 x5, 60 x2) with the sound copied as is; the clip keeps the original
  in `smoothFrom` to switch back. On a known 120 fps clip the made frames are 2.3 times closer to
  the real ones than repeating a frame.
- **Audio:** music, voiceover recording, text to speech, voice effects and enhancer, auto-duck,
  volume keyframes, beat markers (auto and manual), and 36 sound effects in 4 groups
  (`media/SoundFx.kt`): made by a small synthesizer in code, so nothing to license, rendered once
  to WAV in app storage. Export mixes music, sounds and overlay audio into one track first
  (`engine/AudioPremix.kt`), so any number can overlap.
- **Beat detection** (`cpp/beat_tracker.cpp`): bass-weighted multi-band log spectral flux, tempo
  from a mean tempogram with a prior around 120 BPM, dynamic-programming beat placement (Ellis 2007,
  as in librosa) that follows tempo drift, a check that moves beats onto the kick when it locked onto
  off-beat hats, and sub-frame timing with the window lead removed. Scored with a harness against
  librosa on real songs and against synthetic loops with known beats: mean F-measure 0.86 (the
  previous detector scored 0.48), timing within ±2 ms on known beats. Fast or slow songs can come
  out at double or half tempo; the Beat / Half / Bar switch covers that. `Beats.DETECTOR_VERSION`
  makes saved projects detect again when the detector changes.
- **Captions:** on-device, 99 languages with translate-to-English, line editing (split, merge,
  nudge), karaoke highlight, safe-area clamping.
- **Timeline:** per-type lanes, vertical scroll, and a track column with eye, mute and lock per
  track.
- **Preview:** drag, pinch and twist with snap guides; two-finger undo; three-finger redo;
  hold-to-see-original; safe-area overlays for TikTok, Reels and Shorts; save frame at 1080p
  with every layer (`engine/StillRenderer.kt` runs a second player off screen into an ImageReader).
- **Export:** 720p to 4K, 24 to 120 fps, HEVC option, platform presets, GIF (one palette made from
  the video's own colours by median cut, shared by every frame), sound only.
- **Projects:** drafts, duplicate, share and import as `.reelproj`, settings.
- **In-app updates** through small binary patches (see section 6).
- **Crash reporting** with Sentry, release builds only.

---

## 5. Testing

- **JVM unit tests:** 100, in `app/src/test`. Run with `gradle :app:testDebugUnitTest`. The GIF and
  update-patch tests shell out to `python3` (and Pillow); they are skipped if those are missing.
- **Device tests:** 35, in `app/src/androidTest`. They run on an emulator or a phone with
  `gradle :app:assembleDebugAndroidTest`, then
  `adb shell am instrument -w com.reel.editor.debug.test/androidx.test.runner.AndroidJUnitRunner`.
  They export real videos and check pixels: layers, overlays, adjustment layers, crop, face blur,
  reverse, GIF, sound only, project packing, head tracking on a real interview clip (a public-domain
  NASA video in `androidTest/assets/talk.mp4`), voice, fonts and captions.
- **Shader catalog:** `FxCatalogTest` (JVM) checks `library.json` and `transitions.json`: unique ids
  and names, well-formed params, and no GLSL ES 3 constructs (`texture(`, `round(`, `%`, shifts,
  `switch`, `uint`...), which GLSL ES 1.00 drivers reject. Transitions have no shared helpers: a
  transition that needs `rand`, `noise` or `PI` defines its own (`rand_`, `noise_`, `PI_`).
- **Shaders:** `python3 tools/fx/validate_shaders.py` compiles every GLSL file with
  glslangValidator. `tools/fx/render_check.py <dir>` renders every filter, effect, animation and
  transition on a headless GPU (Mesa through EGL) with the app's own source assembly, on the bundled
  portrait and apple pictures. It checks that filters change the picture, effects move, animations
  end on the untouched clip and transitions start on clip A and end on clip B, and it writes contact
  sheets for a visual pass. It takes about a minute. `FxThumbsDeviceTest` runs the same checks on a
  phone's GPU. Known subtle ones that the checks flag on purpose: Chromatic, Smooth, Film grain.

- **1.9.0 compatibility pass:** 105 JVM tests and lint (0 errors) pass. All native libraries in the
  APK are 16 KB aligned (`readelf` LOAD align 0x4000 on arm64-v8a, armeabi-v7a, x86_64;
  `zipalign -c -P 16` passes), as Android 15+ devices require. On an API 30 x86_64 emulator the app
  installs and launches, and `FxThumbsDeviceTest` passes for all 663 shaders (about 14 minutes on the
  software GPU).

Notes on the emulator used during development (API 30, x86_64, software GPU, `-accel off`):

- It is very slow. Exports take about 50× real time, and "System UI / Reel isn't responding"
  dialogs appear. ANR traces taken at those moments showed the app's main thread idle, so they are
  the emulator, not the app.
- Files pushed with `adb push` plus a media scan can stay `is_pending=1`, which hides them from
  every app. Clear it with
  `adb shell content update --uri content://media/external/<type>/media/<id> --bind is_pending:i:0`.
- It has no text-to-speech engine, so "Read aloud" is untested on device. The code reports
  "No text-to-speech voice on this phone".

---

## 6. Releases and in-app updates

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`. Lint must report 0
   errors.
3. Run `python3 tools/update/make_update.py --new app-release.apk --old <each earlier release APK> --out site`.
   This writes `update.json`, the full APK and one `.rpatch` per older version. Patches are about
   3 MB, against 300 MB for the full APK. The tool re-applies each patch to prove it rebuilds the new
   APK byte for byte.
4. Upload everything in `site/` to one HTTPS folder. A GitHub release works; its stable link is
   `https://github.com/<owner>/<repo>/releases/latest/download/update.json`.
5. Point the app at that `update.json`, either at build time with `-Preel.updateUrl=...` or in the
   app under Settings → Updates. **No update source is configured yet.** The owner has to choose a
   host.

The app checks the manifest on Home, downloads the patch for its own versionCode and rebuilds the
APK from the installed one. It verifies the SHA-256, then installs through `PackageInstaller`. It
falls back to the full APK when no patch fits. Updating from 1.3.0 or older needs one manual install
of 1.4.0 or later, because the updater arrived in 1.4.0.

Release APKs sent to the owner so far: 1.0 through 1.8.0 (1.9.0 is built as debug only; it needs the
release key). Patches exist for 1.4.0 → 1.6.0 and
1.5.0 → 1.6.0.

---

## 7. Known limits and weak spots

- **Reverse** re-encodes the clip. Phones decode in hardware, but no phone timing has been taken.
  On the emulator, 2 s of video took 45 s.
- **Head tracking** uses Android's `FaceDetector` (frontal faces). When it finds none, it falls back
  to the top of the person mask. It is fine for talking heads; it is not a general object tracker.
- The text panel plus the keyboard leaves a small preview on short phones.
- Caption translation only goes into English; that is a limit of the model.
- The `.reelproj` format copies every media file, so packed projects can be large.
- Stabilization cancels shifts and turns, not rolling-shutter wobble or zoom; very large shakes are
  only partly cancelled so the zoom stays under 8 to 22 percent.
- Smooth is slow on the software emulator (13 minutes for 3 s); phones were not timed. Encoders
  that refuse 120 fps at the clip's size are asked for 60 fps instead (that fallback is untested).
- Remove pauses works on clips at one speed (not speed curves) and leaves layers above in place.
- On the software emulator, exports with heavy audio can go more than 25 s between output samples;
  the exporter allows 2 minutes before calling an export stuck.
- Not tapped through on the emulator yet: caption Merge and the Share project send sheet. Their
  logic is covered by unit and device tests.

---

## 8. Backlog (agreed, not built)

- Proxy editing for 4K.
- Tracking any object, not just heads; separating voice from music.
- A royalty-free music library; one-tap video templates. These need licensed content.
- A first-run walkthrough of the gestures; tablet and landscape layout.
- Translating the UI into other languages. All strings are currently hard-coded in Compose and need
  moving to `strings.xml`.

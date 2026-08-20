# Precision Tuner

<p align="center">
  <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-yellow.svg">
  <img alt="Platform: Android" src="https://img.shields.io/badge/Platform-Android-3ddc84.svg">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-24-brightgreen.svg">
  <img alt="AI: pYIN + Tiny CREPE" src="https://img.shields.io/badge/AI-pYIN%20%2B%20Tiny%20CREPE-blueviolet.svg">
</p>

A precision all-in-one tuner for Android: instrument tuner, chromatic tuner with
historical temperaments, a metronome, and an ear-training quiz module — with
AI-assisted pitch detection (pYIN-lite + Tiny CREPE), a full multi-level
settings system with preset themes, and in-app updates.

## Screenshots

| Instrument tuner | Chromatic tuner | Metronome |
| :-: | :-: | :-: |
| <img src="screenshots/instrument.png" width="240" alt="Instrument tuner"/> | <img src="screenshots/chromatic.png" width="240" alt="Chromatic tuner"/> | <img src="screenshots/metronome.png" width="240" alt="Metronome"/> |

## Features

- **乐器调音 (Instrument tuner)** — guitar, bass, ukulele, violin, viola, cello,
  double bass, 中阮, 琵琶, 二胡, 大阮 with automatic string detection, manual
  string selection, per-string piano reference tones, and persistent custom
  tuning presets.
- **半音阶调音 (Chromatic tuner)** — three temperaments: 十二平均律 (12-TET),
  五度相生律 (Pythagorean), 纯律 (just intonation); a **global A4 reference**
  (415–466 Hz) that applies to every tuning mode, the piano reference tone, and
  survives restarts.
- **AI-assisted detection** — hybrid pipeline: FFT coarse localization +
  pYIN-lite refinement, arbitrated against a bundled Tiny CREPE neural model
  (LiteRT) to reject subharmonic errors. Configurable noise gate, sensitivity
  threshold, smoothing window and low-pass filter. The neural model is
  built-time selectable (`tiny` / `small` / `full`).
- **节拍器 (Metronome)** — BPM presets, tap tempo, time signatures, subdivisions
  (eighths / triplets / sixteenths), downbeat accent, circular progress ring,
  and an instant first-click start (no priming delay).
- **视听练耳 (Ear training)** — a game-like quiz module: 单音识别 (note),
  音程听辨 (interval), 和弦听辨 (chord), 音阶听辨 (scale), 五线谱识谱 (staff
  reading) and 节奏听写 (rhythm dictation). Each recognition exercise offers
  无尽 (endless), 挑战 (challenge, five lives) and 测试 (fixed-length test)
  modes with 简单/中等/困难 difficulty presets, played on bundled piano samples
  with game-style feedback (accuracy ring, lives, sound cues).
- **设置 (Settings)** — multi-level sections: 主题 (themes), 调音选项 (tuning),
  调弦预设 (presets), 版本信息 (about). Eight system theme presets (午夜蓝 /
  森林 / 专业 / 石墨玫瑰 / 暖纸 / 海洋 / 黑金 / 樱花粉) plus custom dark/light
  mode with six accent colors; **恢复默认设置** one-tap reset; **检查更新** with
  in-app APK download and auto-install.
- **Visualizations** — live spectrum (power-ratio scale with display ceiling,
  always visible even below the noise gate) and time-domain waveform; three
  gauge styles (precision rail / dial / scrolling pitch waterfall); dark/light
  themes with smooth transitions.

## Demo video script

See [docs/demo-script.md](docs/demo-script.md) for a scene-by-scene guide to
recording a feature walkthrough video.

## Build

The repo pins a writable Gradle home into the workspace (see `build.sh` for the
read-only-`~/.gradle` workaround) and uses Android Studio's bundled JDK:

```bash
./build.sh ./gradlew assembleDebug                     # normal build (DSP-only)
./build.sh ./gradlew -PtinyCrepeEnabled=true assembleDebug            # + CREPE (arm64)
./build.sh ./gradlew -PtinyCrepeEnabled=true -PcrepeModel=small assembleDebug
./build.sh ./gradlew testDebugUnitTest                 # unit tests
```

Install to a connected device (CREPE enabled by default; pass `--no-crepe` to
install a DSP-only build):

```bash
./install-device.sh DEVICE_SERIAL
./install-device.sh DEVICE_SERIAL --no-crepe
```

The CREPE build is arm64-only; the `--no-crepe` build keeps all ABIs. See
`tools/crepe/README.md` for the reproducible model conversion pipeline
(weights are kept out of Git; hashes are recorded).

## Requirements

- Android 7.0+ (minSdk 24), 64-bit arm for the CREPE build.
- Microphone permission for tuning.

## 联系作者

- QQ: 1005028266 (app 内"版本信息"页可点击复制)

## License

MIT — see [LICENSE](LICENSE).

### Third-party notices

- **Tiny CREPE** (neural pitch model) — MIT, © 2018 Jong Wook Kim
  (`app/src/main/assets/licenses/CREPE_LICENSE.txt`); pretrained weights from
  [marl/crepe](https://github.com/marl/crepe), conversion recorded in
  `tools/crepe/convert_crepe.py`.
- **Kenney Interface Sounds** (in-tune cue) — CC0, [kenney.nl](https://kenney.nl)
  (`app/src/main/assets/licenses/KENNEY_INTERFACE_SOUNDS_LICENSE.txt`).
- **Piano reference-tone samples** — Splendid Grand Piano (Steinway), **Public
  Domain samples by AKAI**, conversion by [kinwie](https://github.com/sfzinstruments/SplendidGrandPiano)
  (`app/src/main/assets/licenses/REFERENCE_PIANO_LICENSE.txt`).

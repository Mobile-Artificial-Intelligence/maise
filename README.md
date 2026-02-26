<div align="center" id = "top">
  <img alt="logo" height="200px" src="https://raw.githubusercontent.com/Mobile-Artificial-Intelligence/maid/main/assets/graphics/logo.svg">
</div>

# Maise - Mobile Artificial Intelligence Speech Engine

Maise is an open-source Android speech engine that provides high-quality, on-device text-to-speech synthesis. It is implemented as an Android system TTS service, meaning it works out of the box with any app that uses the standard Android `TextToSpeech` API — no special integration required.

## How It Works

Maise runs the full TTS pipeline locally on the device using [ONNX Runtime](https://github.com/microsoft/onnxruntime):

1. **Text normalization** — raw input text is cleaned and normalized (numbers, abbreviations, punctuation, etc.)
2. **Phonemization** — [Open Phonemizer](https://github.com/NeuralVox/OpenPhonemizer) converts normalized text into phoneme sequences
3. **Synthesis** — phonemes are fed into one of two neural TTS models to produce a raw PCM audio waveform:
   - [**Kokoro**](https://github.com/hexgrad/kokoro) — a high-quality, multi-lingual TTS model
   - [**Kitten TTS**](https://github.com/KittenML/KittenTTS) — an alternative English TTS model
4. **Streaming playback** — sentences are synthesized and played concurrently using a producer-consumer pipeline so audio starts playing before the full text has been synthesized

Audio output is 24 kHz mono 16-bit PCM.

## Voices

Maise ships with a large collection of voices across both engines and multiple languages.

### Kokoro Voices

| Language | Voices |
|---|---|
| English (US) | alloy, aoede, bella, heart, jessica, kore, nicole, nova, river, sarah, sky, adam, echo, eric, fenrir, liam, michael, onyx, puck, santa |
| English (UK) | alice, emma, isabella, lily, daniel, fable, george, lewis |
| German | dora, alex, santa |
| French | siwis |
| Greek | alpha-f, beta-f, omega-m, psi-m |
| Italian | sara, nicola |
| Japanese | alpha-f, gongitsune, nezumi, tebukuro, kumo |
| Portuguese (BR) | dora, alex, santa |
| Chinese (Simplified) | xiaobei, xiaoni, xiaoxiao, xiaoyi, yunjian, yunxi, yunxia, yunyang |

### Kitten TTS Voices

| Language | Voices |
|---|---|
| English (US) | bella, jasper, luna, bruno, rosie, hugo, kiki, leo |

The default voice is `en-US-heart-kokoro`.

## App

The Maise app provides a simple interface for:
- Selecting a voice from the full list
- Entering text and previewing speech synthesis directly in-app
- Opening Android TTS settings to configure Maise as the system default

The selected voice is persisted and shared with the background TTS service so your preference is respected system-wide.

## Cloning

```bash
git clone https://github.com/Mobile-Artificial-Intelligence/maise.git
```

## Building

```bash
./gradlew :app:assembleRelease
```

The output APK will be at:
- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug: `app/build/outputs/apk/debug/app-debug.apk`

## Setup

To use Maise as your system TTS engine, set it as the default in your device settings:

```
Settings > Accessibility > Text-to-Speech Output
```

Select **Maise** as the preferred engine. After that, any app using the Android `TextToSpeech` API will use Maise automatically.

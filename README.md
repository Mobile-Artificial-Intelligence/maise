<div align="center" id = "top">
  <img alt="logo" height="200px" src="https://raw.githubusercontent.com/Mobile-Artificial-Intelligence/maid/main/assets/graphics/logo.svg">
</div>

# Maise - Mobile Artificial Intelligence Speech Engine

Maise is an open-source Android speech engine that provides high-quality, on-device text-to-speech synthesis and automatic speech recognition. The TTS component is implemented as an Android system TTS service, meaning it works out of the box with any app that uses the standard Android `TextToSpeech` API — no special integration required. The ASR component is implemented as an Android `RecognitionService`, compatible with any app using the standard `SpeechRecognizer` API.

## How It Works

All processing runs fully on-device using [ONNX Runtime](https://github.com/microsoft/onnxruntime).

### Text-to-Speech

1. **Text normalization** — raw input text is cleaned and normalized (numbers, abbreviations, punctuation, etc.)
2. **Phonemization** — [Open Phonemizer](https://github.com/NeuralVox/OpenPhonemizer) converts normalized text into phoneme sequences
3. **Synthesis** — phonemes are fed into [**Kokoro**](https://github.com/hexgrad/kokoro), a high-quality multi-lingual neural TTS model, to produce a raw PCM audio waveform
4. **Streaming playback** — sentences are synthesized and played concurrently using a producer-consumer pipeline so audio starts playing before the full text has been synthesized

Audio output is 24 kHz mono 16-bit PCM.

### Automatic Speech Recognition

1. **Recording** — 16 kHz mono 16-bit PCM audio is captured from the microphone
2. **Log-mel spectrogram** — a Whisper-compatible 80-band log-mel spectrogram is computed on-device
3. **Transcription** — the spectrogram is fed through [**distil-whisper/distil-small.en**](https://github.com/huggingface/distil-whisper), an encoder-decoder Transformer model, using greedy decoding to produce the transcribed text

## Voices

Maise ships with a large collection of Kokoro voices across multiple languages.

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

The default voice is `en-US-heart-kokoro`.

## App

The Maise app provides a simple interface for:
- Selecting a voice from the full list
- Entering text and previewing speech synthesis directly in-app
- Opening Android TTS settings to configure Maise as the system default

The selected voice is persisted and shared with the background TTS service so your preference is respected system-wide.

## Setup

### Text-to-Speech

To use Maise as your system TTS engine, set it as the default in your device settings:

```
Settings > Accessibility > Text-to-Speech Output
```

Select **Maise** as the preferred engine. After that, any app using the Android `TextToSpeech` API will use Maise automatically.

### Automatic Speech Recognition

To use Maise as your system speech recognizer, set it as the default in your device settings:

```
Settings > Apps > Default Apps > Assist & voice input
```

Select **Maise** as the preferred recognizer. After that, any app using the Android `SpeechRecognizer` API will use Maise automatically. The `RECORD_AUDIO` permission must be granted to the app.

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
<div align="center" id = "top">
  <img alt="logo" height="200px" src="https://raw.githubusercontent.com/Mobile-Artificial-Intelligence/maid/main/assets/graphics/logo.svg">
</div>

# Maise - Mobile Artificial Intelligence Speech Engine

Maise is an open-source android speech engine designed to provide a powerful and flexible platform for speech sythesis on the edge. To achive this goal, Maise utilises the [Onnx Runtime](https://github.com/microsoft/onnxruntime) to run multiple bleeding edge models to generate high quality speech. For text processing, [Open Phonemizer](https://github.com/NeuralVox/OpenPhonemizer) is used to convert text into phonemes, which are then fed into the two speech synthesis models ([Kokoro](https://github.com/hexgrad/kokoro) and [Kitten TTS](https://github.com/KittenML/KittenTTS)) to generate the final audio output. Maise is implemented as a android system speech service, allowing it to work out of the box with any android application that uses the system text-to-speech service.

## Cloning
To clone the repository, use the following command:

```bash
git clone https://github.com/Mobile-Artificial-Intelligence/maise.git
```

## Building
To build the project, navigate to the project directory and run:

```bash
./gradlew :app:assembleRelease
```

The output apk will be located in `app/build/outputs/apk/release/app-release.apk` or `app/build/outputs/apk/debug/app-debug.apk`.

## Setup
To use this app as a system speech service, you will need to set it as the default TTS engine in your device settings. You can do this by going to `Settings > Accessibility > Text-to-Speech Output` and selecting Maise as the default engine.
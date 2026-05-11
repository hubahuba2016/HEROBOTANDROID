# HeroBot Android

HeroBot Android is an Android version of the HeroBot project by HubakaGS.

The project focuses on creating a customizable AI/chatbot experience that can run on Android devices.

## Features

* Android app build using Gradle
* Java-based chatbot system
* SQLite support
* Offline chatbot functionality
* Expandable architecture for future AI features
* Built and tested on Linux/Kubuntu

## Current Status

Current version: Early Development

Working features:

* APK building
* Android project structure
* Gradle build system
* Java compatibility fixes

Planned features:

* Improved chatbot responses
* Learning system
* Better UI
* Internet fallback support
* Expanded local database
* RPG/game integration ideas

## Requirements

### Linux / Kubuntu

Install:

* OpenJDK 17
* Android SDK
* Gradle
* ADB (optional for testing)

Check Java version:

```bash
java -version
javac -version
```

## Building the APK

From the project root:

```bash
./gradlew assembleDebug
```

Release build:

```bash
./gradlew assembleRelease
```

APK output locations:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

## Running on Android

Install with ADB:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK manually to your Android device.

## Project Goals

The goal of HeroBot is to create an interactive AI assistant/chatbot that can:

* Run on Android
* Learn and expand over time
* Work offline
* Support future AI integrations
* Become part of larger game and assistant projects

## Development Environment

Main development tools:

* Kubuntu Linux
* VS Code
* Android Studio tools
* Gradle
* OpenJDK 17

## Repository Structure

```text
app/                Android application source
gradle/             Gradle wrapper files
build.gradle        Project build configuration
settings.gradle     Gradle settings
```

## Roadmap

* [x] Android build setup
* [x] APK generation
* [ ] Improved chatbot engine
* [ ] Better Android UI
* [ ] Persistent memory system
* [ ] AI/NLP improvements
* [ ] Voice features
* [ ] Game integration

## Contributing

This project is currently experimental and under active development.

Feedback, ideas, and testing are welcome.

## Author

HubakaGS

Ko-fi:
[ko-fi.com/kubohiki](https://ko-fi.com/kubohiki)

## License

 GNU GENERAL PUBLIC LICENSE Version 3

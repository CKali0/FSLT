# ODS Anagrams

Rack solve, Bingo hunt and Flashcards for the French ODS dictionary (`app/src/main/assets/ods.txt`).

## Build the APK

- **Android Studio:** open this folder, then Build > Build APK(s).
- **Command line:** `./gradlew assembleDebug` (needs JDK 17 and the Android SDK).
  The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
- **GitHub:** push this folder to a repository. The Actions tab builds the APK
  (`.github/workflows/build.yml`); download it from the run's Artifacts.

The APK is debug-signed: fine for installing on your own phone.

# Quorvi Launcher

An Android launcher built around liquid glass, a universal command bar, a real Unix terminal and agents that can actually finish tasks.

Early work in progress. The full spec lives in [`docs/SPEC.md`](docs/SPEC.md).

## Building

Needs JDK 21 and the Android SDK. Create `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug
```

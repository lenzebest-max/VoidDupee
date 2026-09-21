# VoidDupe

Paper 1.21.11 / Java 21 plugin project.

## IMPORTANT GitHub upload layout

Upload the **contents of this folder** to the repository root. The repository root must directly contain:

- `.github/workflows/build.yml`
- `src/`
- `build.gradle`
- `settings.gradle`
- `README.md`

Do NOT put these inside another `VoidDupe-MineKeep-Ready` folder.

## Build

GitHub Actions builds the plugin automatically. The workflow also verifies that the generated JAR contains:

- `plugin.yml`
- `com/voiddupe/VoidDupe.class`

Only use the JAR downloaded from the successful GitHub Actions artifact named `VoidDupe`.

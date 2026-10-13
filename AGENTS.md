# RIFT Repository Guide

## Purpose
This file gives coding agents a reliable starting point for work in this repository. Treat the checked-in source, configuration, and documentation as authoritative. Update this guide when verified project workflows or conventions change.

## Orienting in the repository

- Start by checking the repository root, current Git status, and any applicable `AGENTS.md` files in the path to the files being changed.
- Read the root README and the relevant package, build, or tool configuration before choosing commands or editing code.
- Identify the application entry points and the narrowest relevant source and test areas before making a change.
- Use the repository's existing names, architecture, patterns, and dependency manager. Do not infer the language, framework, folder meanings, or commands from the repository name.
- For a focused task, avoid broad cleanup or unrelated refactors.

## Setup and commands

Use JDK 17 and Android SDK Platform 37. The checked-in Android CI workflow runs:

```sh
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon assembleDebug
```

CI runs the test task before the assemble task in separate steps. For a focused local check, run `./gradlew testDebugUnitTest` and then `./gradlew assembleDebug`. ABI-specific APK outputs are `app/build/outputs/apk/debug/app-{abi}-debug.apk` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; there is no universal APK. These commands are read from the project configuration and CI workflow; do not claim a check passed unless it was actually run and its result observed.

Do not add setup, lint, or test tooling without first checking the existing manifests, dependency catalog, and workflows. Document verified workflow changes in the README and this guide when in scope.

## Implementation practices

- Preserve established behavior and public interfaces unless the task explicitly requires a change.
- Prefer a small, cohesive change that follows nearby code over introducing a new abstraction or dependency.
- Check callers, configuration, and related tests before changing shared code or data formats.
- Keep secrets, credentials, machine-specific paths, and generated or local-only files out of source control.
- Update relevant documentation and examples when behavior, setup, or configuration changes.
- Do not edit generated files directly when the repository provides a source or generation workflow; confirm that workflow first.

## Verification

- Find the narrowest relevant existing check from the repository's scripts, CI configuration, or documentation.
- Run checks appropriate to the change when available. If a check cannot be run, state why and report what was inspected instead.
- For bug fixes, verify the reported failure path and add or update a regression test when the project has a suitable test setup.
- Review the final diff for accidental files, unrelated edits, debug output, secrets, and consistency with this guide.
- Report the files changed and the checks run, including failures or limitations.

## Task-specific guidance

Follow any more specific `AGENTS.md` or `AGENTS.override.md` located in a subdirectory. Keep specialized instructions close to the code they govern, and avoid duplicating repository-wide rules there.

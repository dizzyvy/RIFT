# Dependency and asset inventory

This inventory records licensing and provenance evidence present in this
repository. It is not an exhaustive software bill of materials or legal
conclusion, and it does not establish complete GPLv3 compliance. A dependency
declaration, package name, or single metadata field does not establish all
license obligations for an artifact or its contents.

**Status labels:** **Verified** means the stated claim is supported by the
identified repository evidence. **Partially verified** means some details
are supported but significant details remain unresolved. **Unverified** means
available evidence is insufficient to confirm the claim.

| Component or asset | Role in RIFT | Source, version, or revision | License evidence | Status and remaining work |
|---|---|---|---|---|
| RIFT-authored app and documentation | Android music player and project documentation | Repository files; no complete file-level provenance map | Root `LICENSE` contains GPLv3. The intended designation is GPL-3.0-only only where RIFT holds the relevant rights and the file's provenance supports it. | **Partially verified.** The root license text is present, but rights and provenance have not been established for every file. Review file-level authorship and rights before distribution. |
| Seal-derived downloader source and adaptations | Downloader feature integrated into the Android app | Notices cite `dizzyvy/Seal` commit `7677f61a20fda4210e84215fef9e9b51d25daa47`; no complete file mapping is recorded here. | `THIRD-PARTY-NOTICES.txt` records that the cited upstream project declares GPL version 3. Visible notices were observed in inspected source files; a complete file-by-file review of applicable notices and provenance remains outstanding. | **Partially verified.** The cited provenance and upstream license declaration are recorded in repository notices; every downloader file has not been matched to upstream source. Review provenance, modifications, rights, and distribution implications. |
| `:downloader` Gradle module | Downloader implementation consumed by the app | Included in `settings.gradle.kts`; app declares `implementation(project(":downloader"))` | No single module-wide license can be inferred for all its contents. | **Verified** as a project/module dependency from Gradle declarations; contents have mixed or unresolved provenance and licensing. |
| `youtubedl-android:library` | Downloader library dependency | `io.github.junkfood02.youtubedl-android:library`; version catalog value `0.17.3` | Gradle declaration establishes coordinates/version only. No artifact-specific license determination is made by that declaration. | **Partially verified.** Coordinate and declared version are confirmed; exact artifact, bundled content, and applicable license obligations need review. |
| `youtubedl-android:ffmpeg` | Downloader FFmpeg integration/artifact | `io.github.junkfood02.youtubedl-android:ffmpeg`; version catalog value `0.17.3` | Gradle declaration establishes coordinates/version only; it does not establish the license of any packaged FFmpeg/native components. | **Partially verified.** Coordinate and declared version are confirmed; inspect the exact artifact, native payloads, notices, and distribution terms. |
| `youtubedl-android:aria2c` | Downloader aria2c integration/artifact | `io.github.junkfood02.youtubedl-android:aria2c`; version catalog value `0.17.3` | Gradle declaration establishes coordinates/version only; it does not establish the license of any packaged aria2c/native components. | **Partially verified.** Coordinate and declared version are confirmed; inspect the exact artifact, native payloads, notices, and distribution terms. |
| Material color science helper sources | Color utilities in downloader source | Files under `downloader/src/main/java/io/material/` | Source headers identify Google LLC, 2021 and Apache License 2.0; full text is preserved in `licenses/APACHE-2.0.txt`. | **Verified** for the visible headers and corresponding license text. Complete upstream file-by-file provenance comparison has not been performed. |
| Material motion helper sources | Motion helpers in downloader source | Files under `downloader/src/main/java/com/dizzyvy/rift/downloader/ui/common/motion/` | Source headers identify SOUP, 2021 and Apache License 2.0; full text is preserved in `licenses/APACHE-2.0.txt`. | **Verified** for the visible headers and corresponding license text. Complete upstream file-by-file provenance comparison has not been performed. |
| Libre Franklin | Bundled app font resources | Four font files under `app/src/main/res/font/`; bundled version/revision not established here | `licenses/OFL-Libre-Franklin.txt` contains the SIL Open Font License 1.1 text and a LibreFranklin Project Authors notice. | **Partially verified.** Font files and license text are present; exact font-file provenance, version, and distribution packaging should be checked. |
| Shrikhand | Bundled app font resource | `shrikhand_regular.ttf` under `app/src/main/res/font/`; bundled version/revision not established here | `licenses/OFL-Shrikhand.txt` contains the SIL Open Font License 1.1 text and a Jonny Pinhorn notice. | **Partially verified.** Font file and license text are present; exact font-file provenance, version, and distribution packaging should be checked. |
| Other direct Gradle dependencies | App and downloader UI, storage, networking, persistence, and playback support | Direct dependencies are declared in `app/build.gradle.kts`, `downloader/build.gradle.kts`, and `gradle/libs.versions.toml`; examples include AndroidX/Jetpack, Compose, Media3, Kotlin, Accompanist, Coil, Koin, Room, OkHttp, MMKV, JUnit, and Robolectric. | These project declarations and the catalog are not a complete license inventory for resolved artifacts or transitives. No per-artifact license conclusion is asserted here. | **Partially verified.** Declared dependency families and catalog entries are available locally. Review exact resolved versions, metadata/notices, transitives, and distribution terms for each release. |
| RIFT branding artwork | App and repository branding | `branding/rift-logo.svg` and `branding/rift-logo-transparent.png` | This inventory makes no license or permission claim about the artwork. | **Unverified.** Ownership, standalone reuse, modification, trademark, and other permissions were not established; separate logo terms are outside this inventory's scope. |

## Outstanding audits

The following work remains outstanding and must not be inferred as complete
from this document:

- Complete resolved dependency-graph review.
- Full transitive-license review.
- Native-library and bundled-payload review.
- Complete file-by-file upstream provenance review where applicable.
- Final review of applicable distribution obligations for each release.

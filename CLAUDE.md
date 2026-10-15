# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

The reference project for `bayern.kickner` Kotlin/JVM projects. It shows how to use the two in-house libraries,
[Klogger](https://github.com/nexus421/Klogger) (logging) and [KotNexLib](https://github.com/nexus421/KotNexLib)
(crypto and Kotlin extensions), from a plain Kotlin/JVM Gradle app, and it documents the conventions every new
project follows. **Read [README.md](README.md) before touching library-related code.** It documents the verified
API surface of both libraries, which extension functions are deprecated, the opt-in annotations required, and the
conventions (toolchain, code style, documentation, structure of a service) in full.

Entry point: [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt). Packages under `bayern.kickner.demo`:
`logging` (Klogger setup), `crypto` (hashing and encryption demos), `vault` (a small in-memory app tying both
libraries together with sealed-class domain modeling). [KNot](https://github.com/nexus421/KNot) is the worked
example of a complete service built with the same conventions. **For Android apps read [ANDROID.md](ANDROID.md)**:
toolchain (AGP 9, built-in Kotlin, legacy-kapt), the Contract, ViewModel and View screen pattern, Navigation 3,
ObjectBox, backup and the rules for keeping secrets out of the APK and the repository.

## Commands

Use the Gradle wrapper (`./gradlew`), not a system-installed Gradle.

```bash
./gradlew build
```

```bash
./gradlew run
```

Pass demo arguments (each `key=value` is its own argument):

```bash
./gradlew run --args="user=grace.hopper password=my-very-secret-passphrase"
```

There are no tests, the project exists to be read. `./gradlew test` is wired up (`kotlin.test` on the JUnit
Platform) but has nothing to run.

## Toolchain

- Kotlin JVM plugin 2.4.20. JVM toolchain: Amazon Corretto 25 (LTS), vendor pinned via `JvmVendorSpec.AMAZON` in
  `build.gradle.kts`. Bump to the next Corretto LTS when it ships.
- Gradle 9.6.0 via wrapper.
- Version `1.0.0` in `build.gradle.kts`. A Gradle task generates `bayern.kickner.demo.BuildConfig` (`VERSION`,
  `BUILD_TIME`) into `build/generated/buildConfig/kotlin` on every build. Never commit it.
- Custom Maven repository `nexus421MavenReleases` (`https://maven.kickner.bayern/releases`) in addition to Maven
  Central, for `bayern.kickner:Klogger` and `bayern.kickner:KotNexLib`.
- `-Xexplicit-context-arguments` is enabled, so Kotlin's still Experimental context parameters are usable. See
  README.md's "Context parameters" section for when that is a good idea.
- CI: `.github/workflows/build.yml` runs `./gradlew build` on every push.

## Code style

Deliberate conventions for `bayern.kickner` projects, see README.md's "Conventions" section for the reasoning:

- `.not()` instead of `!`.
- Small `if`s without braces. Assign a condition to a `val` first, then branch on it. Prefer `if` and `when` as
  expressions assigned to a `val` over mutating a `var` from inside branches.
- `runCatching` by default. Plain `try`/`catch` only when several exception types need genuinely different
  recovery, with the reason in the KDoc (one example in `vault/PasswordPolicy.kt`).
- Expected business outcomes are return values (`ResultOf2`, `ResultOfTripple`, sealed error types), never
  exceptions.
- Sealed classes and interfaces to make illegal states unrepresentable, instead of nullable fields plus runtime
  checks.
- Explicit backing fields (`val x: PublicType` with `field = ConcreteType()`) over a private property plus public
  getter, but only when the backing collection is concurrency-safe itself (`CopyOnWriteArrayList`).
- Prefer KotNexLib's extension functions over hand-rolled equivalents, and the Kotlin standard library over
  KotNexLib where both exist (`partition()` not `splitFilter`, stdlib `toHexString()`).
- Top-level code logs through `staticLog` with a `private const val TAG` per file, classes and objects through the
  receiver helpers (`infoLog { }` and friends). Never log secrets.
- Time and randomness are injectable parameters with defaults (`clock`, `now`, `random`).
- Every printed timestamp uses `dd.MM.yyyy HH:mm:ss z` with `Locale.ENGLISH` through the one shared
  `timestampFormat`, e.g. `19.09.2026 18:40:12 CEST`.
- KDoc on every public declaration, with `@param` and `@property` where the name alone does not say it. Inline
  comments are one short sentence and only where the reason is not obvious.
- No em dashes, no en dashes and no semicolons in any text: README, KDoc, comments, strings, tests. Two sentences,
  a colon, a comma or parentheses instead.
- Code, KDoc, comments and READMEs are English.
- If a project needs a UI at all, default to Compose (Compose Multiplatform on JVM/Desktop, Jetpack Compose on
  Android, conventions in ANDROID.md).

Keep README.md, ANDROID.md and KDoc in sync with the code in the same change.

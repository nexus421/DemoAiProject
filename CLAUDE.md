# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is a demo/reference project (`bayern.kickner`) showing how to use two in-house libraries,
[Klogger](https://github.com/nexus421/Klogger) (logging) and
[KotNexLib](https://github.com/nexus421/KotNexLib) (crypto + Kotlin extensions), from a plain
Kotlin/JVM Gradle app. **Read [README.md](README.md) before touching library-related code** — it
documents the verified API surface of both libraries (including places where their own upstream
docs are out of date), which extension functions/classes are available, opt-in annotations
required, and gotchas that aren't obvious from the code alone.

Entry point: [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt). Packages under
`bayern.kickner.demo`: `logging` (Klogger setup), `crypto` (hashing/encryption demos), `vault`
(a small in-memory app tying both libraries together with sealed-class domain modeling).

## Commands

Use the Gradle wrapper (`./gradlew`), not a system-installed Gradle.

```bash
./gradlew build
```

```bash
./gradlew run
```

Pass demo args (each `key=value` is its own argument):

```bash
./gradlew run --args="user=grace.hopper password=my-very-secret-passphrase"
```

```bash
./gradlew test
```

Run a single test class or method with `--tests`:

```bash
./gradlew test --tests "bayern.kickner.SomeClassTest"
```

```bash
./gradlew test --tests "bayern.kickner.SomeClassTest.someMethod"
```

Tests are not a priority in this project — it exists to be read as a reference, not to be tested.

## Toolchain

- Kotlin JVM plugin 2.4.10. JVM toolchain: Amazon Corretto 25 (LTS), vendor pinned via
  `JvmVendorSpec.AMAZON` (`build.gradle.kts`) — a personal default for `bayern.kickner` projects,
  bump to the next Corretto LTS when one ships rather than leaving this stale. See README.md's
  "JDK" section.
- Gradle 9.6.0 via wrapper (`gradle/wrapper/gradle-wrapper.properties`).
- Test framework: `kotlin("test")` on JUnit Platform (`useJUnitPlatform()` is set in `build.gradle.kts`).
- Group/coordinates: `bayern.kickner:DemoAiProject:1.0-SNAPSHOT`.
- Custom Maven repo `nexus421MavenReleases` (`https://maven.kickner.bayern/releases`), in addition
  to Maven Central, for `bayern.kickner:Klogger` and `bayern.kickner:KotNexLib`.
- `-Xexplicit-context-arguments` is enabled (`compilerOptions.freeCompilerArgs`), so Kotlin's
  (still Experimental) context parameters are usable — see README.md's "Context parameters"
  section for when that's actually a good idea.

## Code style

These are deliberate conventions for this repo, not defaults — keep following them:

- `.not()` instead of `!`.
- Small `if`s without braces; assign a condition to a `val` first, then branch on it. Prefer
  `if`/`when` as expressions assigned to a `val` over mutating a `var` from inside branches.
- `runCatching` by default; plain `try`/`catch` only when multiple exception types need genuinely
  different recovery, always with a one-line comment explaining why (one example exists in
  `vault/PasswordPolicy.kt`).
- Sealed classes/interfaces to make illegal states unrepresentable, instead of nullable fields plus
  runtime checks.
- Explicit backing fields (`val x: PublicType` / `field = ConcreteType()`) over a private-property-
  plus-public-getter pair — but only when the backing collection is itself concurrency-safe (e.g.
  `CopyOnWriteArrayList`), since the narrowed type still exposes the same instance, not a copy. See
  README.md's Code style section for the worked example.
- Prefer KotNexLib's extension functions over hand-rolled equivalents — see README.md for what's
  available before writing a new one.
- If a project needs a UI at all, default to Compose (Compose Multiplatform on JVM/Desktop, Jetpack
  Compose on Android) over Swing/AWT/JavaFX/Views — see README.md's "Recommended companion
  libraries" section.

No test suite exists here to verify against — see README.md's Quick start for how to actually run
and observe the demo instead.

# DemoAiProject

[![Build](https://github.com/nexus421/DemoAiProject/actions/workflows/build.yml/badge.svg)](https://github.com/nexus421/DemoAiProject/actions/workflows/build.yml)
![Kotlin](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnexus421%2FDemoAiProject%2Fmaster%2Fbuild.gradle.kts&search=kotlin%5C%28%22jvm%22%5C%29%20version%20%22%28%5B%5E%22%5D%2B%29%22&replace=%241&label=Kotlin&logo=kotlin&logoColor=white&color=7F52FF)
![JDK](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2Fnexus421%2FDemoAiProject%2Fmaster%2Fbuild.gradle.kts&search=JavaLanguageVersion%5C.of%5C%28%28%5Cd%2B%29%5C%29&replace=%241%20%28Corretto%29&label=JDK&logo=openjdk&logoColor=white&color=ED8B00)

The reference project for `bayern.kickner` Kotlin/JVM projects. It shows, with small runnable code, how to pull in
and use the two in-house libraries, and it documents the conventions every new project follows:

- **[Klogger](https://github.com/nexus421/Klogger)** `0.1.0`: tiny logging utility with a DSL.
- **[KotNexLib](https://github.com/nexus421/KotNexLib)** `4.4.1`: crypto and general-purpose Kotlin extensions.

Always check for the latest versions of every library used. Everything below was checked against the tagged source
of both libraries (`Klogger@v0.1.0`, `KotNexLib@v4.4.1`), not only their READMEs. For a complete service built with
these conventions (config file, Ktor server, systemd unit, CLI commands) see [KNot](https://github.com/nexus421/KNot).

## Quick start

Requirements: JDK 25 to build. Amazon Corretto is the pinned toolchain, Gradle downloads it if missing.

```bash
./gradlew run                                                     # runs the demo with the built-in user
./gradlew run --args="user=grace.hopper password=my-very-secret"  # each key=value is its own argument
./gradlew build                                                   # GitHub Actions runs the same on every push
```

The demo hashes and encrypts a secret with every supported mode, registers and logs in a few users in an in-memory
vault and prints a report. Watch the `⚠ ALERT` lines: they come from a second log destination reacting to the same
log calls as the console. The start banner shows version and build time from the generated `BuildConfig`.

## Project layout

| File                                                                                     | Shows                                                                            |
|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt)                                   | Entry point, `ArgsInterpreter`, `BuildConfig`, `staticLog` vs. receiver-based log helpers |
| [logging/DemoLogging.kt](src/main/kotlin/bayern/kickner/demo/logging/DemoLogging.kt)     | Klogger setup with three simultaneous destinations (console plus two custom interceptors) |
| [crypto/HashingDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/HashingDemo.kt)       | Plain JVM hashing vs. BouncyCastle hashing, iterative hashing                    |
| [crypto/EncryptionDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/EncryptionDemo.kt) | AES CBC, GCM and ECB, `AESData` serialize and restore, legacy Blowfish           |
| [vault/VaultModels.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultModels.kt)         | Sealed interfaces modeling accounts and domain errors                            |
| [vault/VaultService.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultService.kt)       | `ResultOf2` and `ResultOfTripple` for expected business outcomes                 |
| [vault/PasswordPolicy.kt](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt)   | The one deliberate `try`/`catch` (instead of `runCatching`) in this repo, with rationale |
| [vault/VaultReport.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultReport.kt)         | `handleSizes()` and `forEachDoLast()` collection extensions, stdlib `partition()` |

This project has no tests: it exists to be read. `kotlin.test` on the JUnit Platform is wired up in
[build.gradle.kts](build.gradle.kts) anyway, so `./gradlew test` works as soon as a test file appears.

## Conventions

Deliberate choices for `bayern.kickner` projects, not requirements of the libraries. Follow them when extending this
project or starting a new one.

### Toolchain and build

- Kotlin JVM plugin in its current version, Gradle through the wrapper (`./gradlew`, never a system Gradle).
- JDK: the current [Amazon Corretto](https://aws.amazon.com/corretto/) LTS (25 as of writing, the next one is due
  around September 2027). [build.gradle.kts](build.gradle.kts) pins the vendor as well as the language version, so
  toolchain resolution cannot substitute another distribution. The `foojay-resolver-convention` plugin in
  [settings.gradle.kts](settings.gradle.kts) downloads a matching JDK if none is installed. Bump `languageVersion`
  when the next LTS ships.
- Version: plain `x.y.z` in `build.gradle.kts`, no `-SNAPSHOT`.
- `BuildConfig` is generated by Gradle on every build with `VERSION` (from the build script) and `BUILD_TIME` (Unix
  milliseconds). It lives under `build/`, is never committed and works everywhere: IDE, `gradlew run`, tests and
  the JAR. Reading the version from the JAR manifest would not. See the `GenerateBuildConfig` task in
  [build.gradle.kts](build.gradle.kts) and `appVersion` in [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt).
- A plain CLI uses the `application` plugin (this project). A service uses the Ktor Gradle plugin instead, where
  `./gradlew buildFatJar` produces the one JAR that gets deployed (KNot).
- CI: one GitHub Actions workflow per repository, running `./gradlew build` or `./gradlew test` on every push, see
  [.github/workflows/build.yml](.github/workflows/build.yml).

### Code style

- `.not()` instead of `!` for boolean negation.
- Small `if`s without braces. A condition is often assigned to a `val` first (`val tooWeak = ...` and then
  `if (tooWeak) return ...`). `if` and `when` are used as expressions assigned to a `val` rather than mutating a
  `var` from inside branches.
- `runCatching` by default. Plain `try`/`catch` only when several exception types need genuinely different
  recovery, with the reason stated in the KDoc. This repo has exactly one such case:
  [PasswordPolicy.hashWithFallback](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt).
- Expected business outcomes (wrong password, duplicate username) are return values modeled with `ResultOf2`,
  `ResultOfTripple` and sealed error types, never exceptions. See [The `ResultOf` family](#the-resultof-family).
- Sealed classes and interfaces make illegal states unrepresentable instead of nullable fields plus runtime checks.
  `UserAccount` in [VaultModels.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultModels.kt): an ECB account
  without a key cannot be constructed.
- Explicit backing fields over the private-property-plus-public-getter pair:
  ```kotlin
  val allUsers: List<UserAccount>
      field = CopyOnWriteArrayList<UserAccount>()
  ```
  The narrowed public type still exposes the same instance, not a copy, so this is only safe when the backing
  collection is concurrency-safe itself (`CopyOnWriteArrayList` here). Used in
  [DemoLogging.kt](src/main/kotlin/bayern/kickner/demo/logging/DemoLogging.kt) and
  [VaultService.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultService.kt).
- Prefer the libraries' extension functions over hand-rolled equivalents, and the Kotlin standard library over
  KotNexLib where both offer the same thing: `partition()` instead of the deprecated `splitFilter`, the stdlib
  `ByteArray.toHexString()` instead of KotNexLib's.
- Top-level functions and `private const val` for stateless helpers, classes and objects only where state or a log
  tag lives. Top-level code logs through `staticLog` with a `private const val TAG = "..."` per file, code inside
  classes and objects through the receiver helpers (`infoLog { }` and friends).
- Time and randomness are parameters with defaults (`clock: () -> Long = System::currentTimeMillis`,
  `random: SecureRandom = SecureRandom()`, `now: () -> ZonedDateTime = { ZonedDateTime.now() }`), so callers can
  inject them.
- Every timestamp a project prints uses `dd.MM.yyyy HH:mm:ss z` in the host's zone, with `Locale.ENGLISH` so the
  zone abbreviation never localizes, e.g. `19.09.2026 18:40:12 CEST`. One shared `timestampFormat` per project, see
  [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt).
- Secrets (passwords, API keys, SMTP credentials) are never logged. Log the name of the target instead.
- If a project needs a UI at all: Compose, by default. Compose Multiplatform on JVM/Desktop, Jetpack Compose on
  Android (see [Android companion libraries](#android-companion-libraries)). Something else only when Compose
  genuinely does not fit, e.g. an existing Swing codebase.

### Documentation and text

- Conversation and concept documents may be German. Code, KDoc, comments, log messages and READMEs are English.
- KDoc on every public declaration: what it does, `@param` and `@property` where the name alone does not say it,
  and the reason behind non-obvious decisions. Multi-line explanations belong into the KDoc, not into inline
  comments.
- Inline comments are one short full sentence, only where the reason is not obvious from the code itself.
- No em dashes, no en dashes and no semicolons in any text: README, KDoc, comments, strings, tests. Split into two
  sentences, or use a colon, a comma or parentheses. Plain hyphens in identifiers, flags and `key=value` are fine.
- README and KDoc are updated in the same change as the code. Stale documentation is always wrong.
- README of a service, in this order: badges, two paragraphs of what and why, Quick start, Command line (arguments
  and exit codes), Configuration (tables with field, type, default and description), Endpoints, Behaviour,
  Deployment, Not in scope. [KNot's README](https://github.com/nexus421/KNot#readme) is the template.

### Structure of a service

KNot is the worked example, these are the parts every service shares:

- One package per concern under `bayern.kickner.<project>`: `cli`, `config`, `mail`, `notify`, `routes` and so on.
  `Main.kt` only wires them together.
- One JSON config file, read once at startup into `kotlinx.serialization` data classes with defaults. Unknown
  keys are errors, all validation issues are reported at once, and secrets never appear in error messages.
  `config.example.json` is versioned, `config.json` is gitignored because it holds the secrets.
- Command-line arguments are `key=value` pairs parsed with KotNexLib's `ArgsInterpreter`. Bare words such as `key`
  are checked with `args.contains(...)`.
- Exit codes: `0` on success, `1` for a failed command, `78` (`EX_CONFIG` from sysexits.h) for a rejected config or
  a port that cannot be bound. The systemd unit lists `78` in `RestartPreventExitStatus`, so systemd does not loop
  on a problem a restart cannot fix.
- Deployment: fat JAR via `buildFatJar`, a systemd unit in the repository, TLS left to a reverse proxy, the
  server bound to `127.0.0.1` by default.
- Ktor server on CIO with `kotlinx.serialization`. Ktor logs through SLF4J, so `slf4j-simple` is a `runtimeOnly`
  dependency with its default level set to `warn` at startup.

### Context parameters

[Context parameters](https://kotlinlang.org/docs/context-parameters.html) let a function declare an implicit
dependency that Kotlin resolves from the surrounding scope by type instead of threading it through every call:

```kotlin
context(users: UserService)
fun outputMessage(message: String) {
    users.log("Log: $message")
}

fun main() {
    val users = object : UserService { /* ... */ }
    context(users) {
        outputMessage("Looking up the first user")
    }
}
```

Use them where they make a real dependency chain simpler: the same ambient value (a service, a scope, a
transaction) would otherwise be forwarded through several layers that do not use it themselves. A regular
parameter stays the clearer choice whenever the value differs per call. Still Experimental in Kotlin 2.4, so
[build.gradle.kts](build.gradle.kts) enables `-Xexplicit-context-arguments`. This repo does not use them anywhere,
nothing here has a dependency chain that would benefit.

## Klogger

### Setup

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "nexus421MavenReleases"
        url = uri("https://maven.kickner.bayern/releases")
    }
}

dependencies {
    implementation("bayern.kickner:Klogger:0.1.0")
}
```

`kotlinx-coroutines-core` and `kotlinx-serialization-json` ship as transitive dependencies of Klogger, nothing else
is needed, not even for the Loki appender.

### Log levels

`DEBUG < INFO < WARN < ERROR < CRASH`, filtered by `minLevel` (or not at all when `debug = true`). Message lambdas
are not evaluated when filtered out, so a filtered `debugLog { "$x expensive to build" }` costs nothing.
`KLogger.Level` is an enum in that order, so `level >= KLogger.Level.WARN` works.

### Two ways to log

1. **Receiver extensions**, the tag is the calling class's simple name, from inside any class or object:
   ```kotlin
   debugLog { "message" }
   infoLog { "message" }
   warnLog { "message" }
   errorLog("message", ex = null, printStackTrace = true, sendAsCrash = false)
   errorLog(ex)   // shorthand: logs ex.stackTraceToString() at ERROR
   ```
2. **`staticLog(level, tag) { }`** for top-level code such as `fun main()`, where there is no receiver and
   therefore no default tag. Keep the tag in a `private const val TAG` per file, see
   [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt).

`KLogger.debug(tag) { }`, `.info(...)`, `.warn(...)`, `.error(...)` and `.crash(...)` take an explicit tag from
inside a class.

### Destinations

| Destination                           | Call                                                | Notes                                                                         |
|---------------------------------------|-----------------------------------------------------|-------------------------------------------------------------------------------|
| Console                               | `logToConsole()`                                    | DEBUG, INFO and WARN to stdout, ERROR and CRASH to stderr                     |
| File                                  | `logToFile(File(...))`                              | Appends, file and parent directories are created                              |
| Custom                                | `logToCustom { level, tag, message -> ... }`        | Any lambda, see below                                                         |
| Cached forwarding                     | `logToCachedForwarding(cacheDir, target = {})`      | Retries a flaky target, persists to disk on failure, flushes on later calls   |
| [Loki](https://grafana.com/oss/loki/) | `logToLoki(lokiBaseUrl, appName, bearerToken, ...)` | Batches and ships to Loki over HTTP in its own coroutine, configurable queue  |

Because ERROR and CRASH go to stderr while everything else goes to stdout, the relative order of an ERROR line
and a same-instant INFO line is not guaranteed once both streams are merged (`2>&1`, a process supervisor). They
are independently buffered streams. Reading them separately is unaffected.

### Custom interceptors, and several at once

`KLogger.configure { }` accumulates destinations: every call inside one `configure { }` block, or across several,
adds another destination, nothing is replaced. Every log call is dispatched to all of them in turn, so console, a
metrics counter, an in-memory ring buffer and Loki can all fire for the same call.

[DemoLogging.kt](src/main/kotlin/bayern/kickner/demo/logging/DemoLogging.kt) runs three: `logToConsole()` plus two
independent `logToCustom { }` interceptors, one collecting every entry into an audit trail, the other counting and
printing WARN and above as alerts. Run `./gradlew run` and watch the `⚠ ALERT` lines appear next to the console
output of the same log calls.

### Other things worth knowing

- Logging before `configure { }` (or without any destination) prints a one-time warning to stderr instead of
  throwing.
- A throwing destination is swallowed (`runCatching` internally), so a broken destination never takes down the
  app or blocks the other destinations.
- `KLogger.formatLogDefault(level, tag, message)` yields `dd.MM.yyyy HH:mm:ss LEVEL/TAG: message` and is used by
  the console and file destinations. Reuse it in your own destination for the same format.

## KotNexLib

### Setup

```kotlin
dependencies {
    implementation("bayern.kickner:KotNexLib:4.4.1")
}
```

BouncyCastle (`bcprov-jdk18on`) and `kotlin-reflect` ship as transitive dependencies, so hashing, Argon2, AES and
Blowfish work without anything extra. Some optional integrations need dependencies you add yourself, see
[Optional integrations](#optional-integrations-bring-your-own-dependency). The repository also ships an `LLM.md`
with a compact overview of the whole API for AI assistants, and `docs/` with per-topic pages.

### Crypto

#### Hashing

```kotlin
import kotnexlib.crypto.*

"secret".hash(HashAlgorithm.SHA_256)                            // plain JVM MessageDigest, hex string
"secret".hashBC(HashAlgorithmBC.SHA3_256)                       // BouncyCastle, hex string, more algorithms
"secret".hashIter(HashAlgorithm.SHA_256, iterations = 100_000)  // repeated hashing
```

| API                                                  | Backing                       | Algorithms                                                                                           |
|------------------------------------------------------|-------------------------------|------------------------------------------------------------------------------------------------------|
| `String.hash(HashAlgorithm)`                         | `java.security.MessageDigest` | `MD5`, `SHA_1`, `SHA_256` everywhere, `SHA_384` and `SHA_512` not on every JVM (Corretto has them)   |
| `String.hashBC(HashAlgorithmBC)`                     | BouncyCastle                  | `MD5`, `SHA_256`, `SHA_384`, `SHA_512`, `SHA3_256`, `SHA3_512`                                       |
| `String.hashIter(HashAlgorithm, iterations=100_000)` | repeated `hash()`             | Brute-force hardening for non-password use. Passwords go through `Argon2Helper`                      |

All of them are plain, unsalted digests: fine for checksums and fingerprints, not for passwords.

#### Password hashing with `Argon2Helper`

```kotlin
val hash = Argon2Helper.hash(password.toCharArray())          // Argon2id, self-contained PHC-like string
val ok = Argon2Helper.verify(password.toCharArray(), hash).getOrDefault(false)
```

- Argon2id via BouncyCastle, constant-time verify. Salt and parameters are embedded in the returned string, so
  only that one string is persisted.
- Tunable: `iterations` (default 3, min 1), `memoryKb` (default 65536, min 8192), `parallelism` (default 1).
  Invalid values throw `IllegalArgumentException`,
  [PasswordPolicy.kt](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt) catches that and falls back to
  the defaults.
- `Argon2Helper.hashForMobileDevices(password)` is a lighter preset (`iterations = 4`, `memoryKb = 16384`).
- Hash a `CharArray`, not a `String`, so the caller can wipe it. Wiping only helps when the `CharArray` is the
  original secret, e.g. from `System.console().readPassword()`. A `String` is immutable and stays in memory no
  matter what happens to arrays derived from it, which is why this demo (password from the command line) does
  not bother. `CharArray.useAndWipe { bytes -> ... }` encodes to UTF-8, runs the block and wipes both arrays.

#### AES

```kotlin
import kotnexlib.crypto.AES

// Password-based, recommended for new code (needs @OptIn, see below):
val data = AES.GCM.encryptWithPassword("secret", "my password", compress = true)
val plain = data.decryptAsString("my password").getOrThrow()

// Explicit key and IV, if you manage key material yourself:
val key = AES.Common.generateSecureAesKeyFromPassword(password, salt)
val iv = AES.Common.generateIV().getOrThrow()
val encrypted = AES.CBC.encrypt("secret", key, iv).getOrThrow()
```

| Nested object | Mode         | Notes                                                                                                                                      |
|---------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `AES.Common`  | helpers      | `generateAESKey()`, `generateSecureAesKeyFromPassword(password, salt, iterations = 600_000)` (PBKDF2-HMAC-SHA256), `generateIV()`, `generateNonce()`, `generateSecureRandom(size)` |
| `AES.CBC`     | CBC, PKCS5   | No integrity check, needs a fresh IV per encryption                                                                                        |
| `AES.GCM`     | GCM (AEAD)   | `@ExperimentalKotNexLibAPI` in 4.4.1 although it is the recommended mode. Authenticated: tampering or a wrong password yields `Result.failure`, never silent garbage. Nonce reuse is catastrophic, the password-based helpers generate a fresh one per call |
| `AES.ECB`     | ECB, PKCS5   | Deterministic (same plaintext, same ciphertext) and weak. Password must be exactly 16 or 32 characters. Legacy interop only                 |

`AES.AESData`, returned by the `encryptWithPassword` helpers, bundles ciphertext, salt, iv or nonce, iterations and
mode. `.toString()` serializes it to one Base64 string that is safe for a database column or config file,
`AES.AESData.restore(str)` reconstructs it, and `.decryptAsString(password)` or `.decryptAsByteArray(password)`
decrypt without tracking salt and iv separately. See
[EncryptionDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/EncryptionDemo.kt) for the full round trip.

#### Blowfish

`object BlowfishEncryption`: 64-bit-block legacy cipher, annotated `@CriticalAPI("Blowfish is considered
insecure...")`, an ERROR-level opt-in that does not compile without `@OptIn(CriticalAPI::class)`. `encrypt` and
`decrypt` for `String` or `ByteArray`, optional GZIP compression. Keep it only for interop with systems that
already use it.

#### Opt-in annotations

```kotlin
kotnexlib.ExperimentalKotNexLibAPI   // RequiresOptIn WARNING, e.g. AES.GCM, the ObjectBox AES converter
kotnexlib.CriticalAPI(reason)        // RequiresOptIn ERROR, e.g. BlowfishEncryption, carries a reason string
```

Opt in with `@OptIn(...)` on the function or class, or `@file:OptIn(...)` for a whole file, as in
[EncryptionDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/EncryptionDemo.kt) and
[VaultService.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultService.kt).

### The `ResultOf` family

All in package `kotnexlib`, all `sealed class`:

| Type                       | Shape                                                                    | Use for                                                          |
|----------------------------|--------------------------------------------------------------------------|------------------------------------------------------------------|
| `ResultOf<T>`              | `Success(value: T)` / `Failure(message: String?, throwable: Throwable?)` | Success or an error message                                      |
| `ResultOf2<T, V>`          | `Success(value: T)` / `Failure(value: V)`                                | Both branches carry a typed value. `VaultService.register(...)`  |
| `ResultOf3<T, V, X>`       | `Success(value: T)` / `Failure(value: V, value2: X)`                     | A failure with two pieces of data                                |
| `ResultOfEmpty<T>`         | `Success` (no payload) / `Failure(value: T)`                             | Operations without a success payload, e.g. delete                |
| `ResultOfTripple<T, V, M>` | `Success(value: T)` / `Warning(value: V)` / `Error(value: M)`            | Three-way outcomes. `VaultService.login(...)`                    |

`ResultOfTripple`'s three type parameters are independent slots, not one success type tagged three ways.
`Warning<X>` is `ResultOfTripple<Nothing, X, Nothing>`, its payload fills the second slot. If a warning should
carry the same data as a success ("logged in, but..."), wrap it in a small data class:
`LoginWarning(account, reason)` in [VaultModels.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultModels.kt)
is exactly that.

Also available: `Result<T>.finally(runCatching = false) { }` runs after a `runCatching` chain like a classic
`finally`. With `runCatching = true` errors inside the block are swallowed, so chained `.finally { }` calls do not
take each other down.

### `runCatching` vs. `try`/`catch`

Default to `runCatching { }`. It composes and pairs with `ResultOf` and `Result`. Reach for `try`/`catch` only when
two or more exception types need genuinely different recovery logic, which a `runCatching { }.fold(...)` chain
makes harder to read. This repo has one such case, with the reasoning in its KDoc:
[PasswordPolicy.hashWithFallback](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt).

Expected business outcomes (wrong password, duplicate username) are not exceptions at all. They are modeled with
`ResultOf2` or `ResultOfTripple`, so neither `runCatching` nor `try`/`catch` is involved. Reserve both for things
that actually throw.

### Extension functions (a selection)

| Receiver         | Function                                                                   | What it does                                                          |
|------------------|----------------------------------------------------------------------------|-----------------------------------------------------------------------|
| `String`         | `coverString(start, end, char = '*')`                                      | Masks the middle of a string, e.g. to log a secret partially          |
| `String`         | `toBase64()` / `fromBase64()` / `fromBase64ToByteArray()`                  | Base64 shortcuts (plus `...OrNull()` variants)                        |
| `String?`        | `compress()` / `decompress()`                                              | GZIP, Base64-wrapped                                                  |
| `String`         | `containsAll(list)` / `containsOneOf(list)` / `equalsOneOf(list)`          | Multi-value string comparisons                                        |
| `String`         | `isDigitOnly` / `isAlphabeticOnly` / `isAlphanumericOnly`                  | Validation properties                                                 |
| `String?`        | `ifNullOrBlank { fallback }`, `ifNotNullOrBlank { }`                       | Null- and blank-aware branching                                       |
| `ByteArray`      | `toBase64()`                                                               | Encoding for logs and storage. For hex use the stdlib `toHexString()` |
| `ByteArray?`     | `compress()` / `decompress()`                                              | GZIP, Base64-wrapped                                                  |
| `CharArray`      | `useAndWipe { bytes -> ... }`                                              | UTF-8 encodes, runs the block, wipes both arrays afterwards           |
| `Boolean`        | `ifTrue { }` / `ifFalse { }`                                               | Side-effecting, chainable (returns the original Boolean)              |
| `Boolean?`       | `orTrue` / `orFalse`                                                       | Null-safe defaults                                                    |
| `T?` (any)       | `ifNull(isNull = {}, notNull = {})`, `ifNull { }`, `ifNotNull { }`, `onNull { }` | `if`/`else`-style branching without an explicit `if`            |
| `T?` (any)       | `tryOrNull { }`, `tryOrDefault(default) { }`                               | Try/catch as an expression                                            |
| `Iterable<T>`    | `forEachDoLast { item, isLast -> }`                                        | Iterate with last-element detection, e.g. for tree output             |
| `Collection<T>`  | `handleSizes()` (`IterableSize.IsEmpty` / `IsOne` / `HasMany`)             | Exhaustive `when` over a collection's size                            |
| `MutableList<T>` | `move(element, toIndex)` / `moveOrAdd(...)` / `addIfAbsent(...)`           | Reorder or add in place                                               |
| top-level        | `measureTime { }` (`ResultTimeMeasure(result, timeMillis)`)                | Timing that still returns the block's value                           |
| top-level        | `getAllSealedSubclassesFrom(KClass)`                                       | Reflection over a sealed hierarchy                                    |

Deprecated in 4.4.1 in favour of the standard library, do not use in new code: `Iterable.splitFilter` (use
`partition()`), `ByteArray.toHexString()` (use the stdlib one), `Iterable.any(predicate)` (stdlib), `runsAsJar`,
`toMillisFrom`. This project uses most of the table above, grep `src/` for a function name to see it in context.

### File management (package `file`, not `kotnexlib`)

`BaseFolder` (working directory), `ConfigFile<T>` (load, store and reload a config object with your own
serialization), `KeyValueConfigFile` (a `ConfigFile<Map<String, String>>` preset), `LogFile` (a minimal alternative
to Klogger with size-based rotation), all under `import file.*`. Not used here, Klogger covers logging and KNot
shows the `kotlinx.serialization` route for config files.

### Terminal output

`ColoredPrinters` with ANSI colors, backgrounds and styles (`CommandLineColors`, `CommandLineBackgroundColors`,
`CommandLineStyles`), `ProgressBar`, `createSpinner(...)`, `Table`, cursor movement and clear helpers, `Prompts`
for interactive input, `Terminal.isLinux()`, `isMacOS()`, `isWindows()`, `supportsAnsiColors()`. Handy for CLI
tools, not used here to keep the demo output easy to read.

### Other utilities worth knowing about

- `IBAN` (`object`): `isValidIban(iban)` (also `String.isValidIban()`), `formatIban(iban)`, `getCountryCode(iban)`,
  `getCheckDigits(iban)`.
- `ArgsInterpreter` (`class`, package `kotnexlib`): `key=value` CLI parsing, used in
  [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt) for the `user=` and `password=` overrides. Also
  `getValueAsInt`, `getValueAsDouble`, `getValueAsBoolean` and flag detection. Bare words are not its business,
  check them with `args.contains(...)`.
- `Cache<K, T>`: thread-safe in-memory cache, optional `maxAge` with automatic cleanup. `LocalCache` keeps one
  such cache per type.
- `TOTP` (package `kotnexlib.security`): `generateSecret()`, `clientGenerateCode(secret)` and
  `serverVerifyCode(...)` with a timing-safe comparison.
- `Math` (`object`): `dotProduct`, `cosineSimilarity`, `List<Double>.normalizeVector()`, `isNormalizedVector()`,
  `powOfTwo()` on `Int`, `Long`, `Float` and `Double`.
- `Permutations`: permutations of a character set, with a multi-threaded variant for large inputs.

### Optional integrations (bring your own dependency)

KotNexLib declares these as `compileOnly` in its own build. Its compiled JAR can call into them, but they are not
on your classpath. Add the matching dependency yourself, or the code compiles and then throws
`NoClassDefFoundError` at runtime:

| Package                                     | Needs                                                | What it gives you                                                                                   |
|---------------------------------------------|------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `kotnexlib.external.Ktor`                   | `io.ktor:ktor-server-core`                           | Self-updating server JAR and a Let's Encrypt ACME plugin, both `@CriticalAPI` (untested). `checkApiKey` is deprecated, use [Ktor's API key auth](https://ktor.io/docs/server-api-key-auth.html) |
| `kotnexlib.external.objectbox.*`            | `io.objectbox:objectbox-kotlin` and `objectbox-java` | `CryptoStringEncryptionWithPassword`, an ObjectBox `PropertyConverter` that AES-encrypts a `String` property at rest (`@ExperimentalKotNexLibAPI`) |
| `kotnexlib.external.KotlinxSerilization`    | `org.jetbrains.kotlinx:kotlinx-serialization-json`   | `EnumAsIntSerializer` and `EnumAsStringSerializer` base classes                                     |
| `kotnexlib.external.G0dkarQrCodeExtensions` | `io.github.g0dkar:qrcode-kotlin`                     | QR code generation extensions                                                                       |
| `kpub`                                      | `io.ktor:ktor-client-cio`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` | `KPubClient` for sending mails and SMS through a [KPub](https://github.com/nexus421/KPub) instance |

## Recommended companion libraries

Not included here (this project only needs crypto and logging), but the natural next additions:

| Need                                            | Library                                                                                                                                       |
|-------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| UI, if a project genuinely needs one            | **Compose, by default.** [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) on JVM/Desktop, Jetpack Compose on Android |
| HTTP server and/or client                       | [Ktor](https://ktor.io/), CIO engine, `ktor-server-test-host` for route tests. KNot is the worked example                                     |
| JSON                                            | `kotlinx.serialization`, pairs with Ktor content negotiation and KotNexLib's enum serializers                                                 |
| Relational database                             | [Exposed](https://github.com/JetBrains/Exposed)                                                                                               |
| Relational database, embedded single file       | Exposed plus the `org.xerial:sqlite-jdbc` driver, no database server needed                                                                   |
| Fast embedded NoSQL alternative                 | [ObjectBox](https://objectbox.io/), pairs with KotNexLib's `CryptoStringEncryptionWithPassword` for encryption at rest                        |
| Mail                                            | `jakarta.mail:jakarta.mail-api` with `org.eclipse.angus:angus-mail` as `runtimeOnly`, see KNot's `MailSender`                                 |

## Android companion libraries

Not usable from this JVM-only project, but two more `bayern.kickner` libraries worth knowing for Android and
Jetpack Compose projects. Both come from the same `nexus421MavenReleases` repository. Each library's own README and
KDoc cover the rest of its surface.

### BarcodeScanner: `BarcodeScannerDialogV3`

```kotlin
dependencies {
    implementation("bayern.kickner:BarcodeScanner:3.0.0")
}
```

[`BarcodeScannerDialogV3`](https://github.com/nexus421/BarcodeScanner) shows a camera preview in an `AlertDialog`
(ML Kit plus CameraX) and hands back every barcode ML Kit found in the accepted frame, not just one decoded string
like the deprecated `BarcodeScannerDialogV2`:

```kotlin
BarcodeScannerDialogV3(this@Activity) { barcodes ->
    val value = barcodes.first().rawValue ?: return@BarcodeScannerDialogV3
    Toast.makeText(this@Activity, value, Toast.LENGTH_SHORT).show()
}
```

Constructing it shows the dialog, there is no separate `.show()`. It is deliberately a plain `class`, not a
`data class`: construction has a side effect, so an accidental `.copy()` would pop a second dialog. Each `Barcode`
carries ML Kit's `format`, `valueType`, `boundingBox` and `cornerPoints`. The camera is a process-wide resource,
so a second dialog constructed while the first is showing reports through `onError` and shows a busy placeholder
instead of stealing the camera.

### ComposeLoadingDialog: the KITT loading indicator

```kotlin
dependencies {
    implementation("bayern.kickner:ComposeLoadingDialog:2.1.2")
}
```

`LoadingDialog(...)` defaults to the KITT style (a Knight Rider running light). Pass `type =` only for `CIRCLE` or
`LINE`. The library's README example (`LoadingDialog(show = ..., type = LoadingDialogType.KITT)`) does not match
the `2.1.2` API: there is no `show` parameter and the enum is `Type`. Checked against the source and the sample app:

```kotlin
import bayern.kickner.loadingdialogs.LoadingDialog

var isLoading by remember { mutableStateOf(false) }

if (isLoading) {
    // type defaults to Type.KITT. Without onDismissRequest the user cannot dismiss it, flip isLoading yourself
    LoadingDialog(msg = "Loading some stuff...")
}
```

`Type` is a generic name for an import. `import bayern.kickner.loadingdialogs.Type as LoadingDialogType` avoids
collisions.

## Sources

- Klogger: [github.com/nexus421/Klogger](https://github.com/nexus421/Klogger), tag `v0.1.0`.
- KotNexLib: [github.com/nexus421/KotNexLib](https://github.com/nexus421/KotNexLib), tag `v4.4.1`.
- KNot: [github.com/nexus421/KNot](https://github.com/nexus421/KNot), the worked example of a service.
- BarcodeScanner: [github.com/nexus421/BarcodeScanner](https://github.com/nexus421/BarcodeScanner), tag `v3.0.0`.
- ComposeLoadingDialog: [github.com/nexus421/ComposeLoadingDialog](https://github.com/nexus421/ComposeLoadingDialog), tag `v2.1.2`.
- Kotlin context parameters: [kotlinlang.org/docs/context-parameters.html](https://kotlinlang.org/docs/context-parameters.html).

# DemoAiProject

A small, functional demo **and reference project** for `bayern.kickner` Kotlin/JVM projects. It
exists to show — with real, runnable code — how to pull in and use two in-house libraries:

- **[Klogger](https://github.com/nexus421/Klogger)** `0.1.0` — tiny logging utility with a DSL.
- **[KotNexLib](https://github.com/nexus421/KotNexLib)** `4.3.0` — crypto + general-purpose Kotlin
  extensions.

Everything documented below was verified against the actual tagged source of both libraries
(`Klogger@v0.1.0`, `KotNexLib@v4.3.0`), not just their READMEs — see
[A note on accuracy](#a-note-on-accuracy) for why that distinction matters.

Also documented here, even though this project doesn't use them itself: a note on
[Context parameters](#context-parameters) (a Kotlin language feature, encouraged where it fits),
and an [Android companion libraries](#android-companion-libraries) section for two more
`bayern.kickner` libraries relevant to Compose projects specifically.

## Quick start

```bash
./gradlew run
```

Optionally override the demo user/password (each `key=value` pair must be its own argument, so
whitespace inside a value has to stay in the same shell-quoted token):

```bash
./gradlew run --args="user=grace.hopper password=my-very-secret-passphrase"
```

## JDK

This project targets [Amazon Corretto](https://aws.amazon.com/corretto/) — specifically, whatever
the **current LTS** release is (Corretto 25 as of writing; next one, Corretto 29, is due around
September 2027). That's a personal default for `bayern.kickner` projects, not something
Klogger/KotNexLib require — both work fine on any compliant JDK. `build.gradle.kts` pins the
vendor, not just the language version, so toolchain resolution can't silently substitute a
different distribution:

```kotlin
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.AMAZON)
    }
}
```

Verified end to end: with `JAVA_HOME` pointing at a *different* JDK (plain OpenJDK 21) so Gradle
itself bootstraps on that, `--info` output still shows `Kotlin compilation 'jdkHome' argument:
/usr/lib/jvm/java-25-amazon-corretto` — the pinned toolchain constraint, not whatever happens to be
on `PATH`, is what actually gets used to compile and run. If Gradle can't find a matching local
install at all, the `org.gradle.toolchains.foojay-resolver-convention` plugin (already in
[settings.gradle.kts](settings.gradle.kts)) auto-downloads one — no manual JDK install required,
though `sudo apt install java-25-amazon-corretto-jdk` (via the
[apt repo](https://docs.aws.amazon.com/corretto/latest/corretto-25-ug/generic-linux-install.html))
works too, and is what was used to verify this.

**When Corretto ships its next LTS, bump `languageVersion` here** — and in `CLAUDE.md`'s Toolchain
section — rather than leaving this pinned to 25 indefinitely.

## Project layout

| File                                                                              | Shows                                                                         |
|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt)                             | Entry point, `ArgsInterpreter`, `staticLog` vs. receiver-based log helpers     |
| [logging/DemoLogging.kt](src/main/kotlin/bayern/kickner/demo/logging/DemoLogging.kt) | Klogger setup with 3 simultaneous destinations (console + 2 custom interceptors) |
| [crypto/HashingDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/HashingDemo.kt) | Plain-JVM hashing vs. BouncyCastle hashing, iterative hashing                  |
| [crypto/EncryptionDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/EncryptionDemo.kt) | AES CBC / GCM / ECB, `AESData` serialize/restore, legacy Blowfish              |
| [vault/VaultModels.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultModels.kt)   | Sealed interfaces modeling accounts & domain errors                            |
| [vault/VaultService.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultService.kt) | `ResultOf2` / `ResultOfTripple` used for expected business outcomes            |
| [vault/PasswordPolicy.kt](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt) | The one deliberate `try/catch` (instead of `runCatching`) in this repo, with rationale |
| [vault/VaultReport.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultReport.kt)   | `handleSizes()`, `splitFilter()`, `forEachDoLast()` collection extensions      |

Tests were intentionally left out of this reference project — it exists to be read, not tested.

---

## Klogger

### Setup

Already wired up in [build.gradle.kts](build.gradle.kts):

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

`kotlinx-coroutines-core` and `kotlinx-serialization-json` ship as transitive `implementation`
dependencies of Klogger — you get them automatically, no extra setup needed even for the Loki
appender.

### Log levels

`DEBUG < INFO < WARN < ERROR < CRASH`, filtered by `minLevel` (or bypassed entirely if `debug =
true`). Message lambdas are **not evaluated** when filtered out, so a filtered `debugLog { "$x
expensive to build" }` costs nothing.

### Two ways to log

1. **Receiver extensions** (tag = calling class's simple name, via `DEFAULT_LOG_TAG`) — use these
   from inside any class/object:
   ```kotlin
   debugLog { "message" }
   infoLog { "message" }
   warnLog { "message" }
   errorLog("message", ex = null, printStackTrace = true, sendAsCrash = false)
   errorLog(ex: Throwable)   // shorthand: logs ex.stackTraceToString() at ERROR
   ```
2. **`staticLog(level, tag) { }`** — for genuine top-level/static contexts (e.g. `fun main()`)
   where there's no `this` and therefore no `DEFAULT_LOG_TAG`. See the top of
   [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt).

There's also the fully explicit `KLogger.debug(tag) { }` / `.info(...)` / `.warn(...)` /
`.error(...)` / `.crash(...)` if you want to pass a custom tag from inside a class.

### Destinations: what's built in

| Destination                                                    | Call                                          | Notes                                                              |
|------------------------------------------------------------------|------------------------------------------------|----------------------------------------------------------------------|
| Console (STDIO)                                                | `logToConsole()`                              | DEBUG/INFO/WARN → stdout, ERROR/CRASH → **stderr**                  |
| File                                                            | `logToFile(File(...))`                        | Appends; file + parent dirs auto-created                            |
| Custom / interceptor                                            | `logToCustom { level, tag, msg -> ... }`      | Arbitrary lambda — see below                                        |
| Cached forwarding                                                | `logToCachedForwarding(cacheDir, target = {})` | Retries a flaky target; persists to disk on failure, flushes on later log calls |
| [Loki](https://grafana.com/oss/loki/)                            | `logToLoki(lokiBaseUrl, appName, bearerToken, ...)` | Batches + ships to Loki via HTTP; own coroutine, configurable queue/flush size |

Gotcha: because ERROR/CRASH go to **stderr** while everything else goes to **stdout**, if you
merge both streams in one terminal (`2>&1`) or through a process supervisor, the *relative*
ordering between an ERROR/CRASH line and a same-instant INFO/WARN line isn't guaranteed — they're
independently buffered streams. Doesn't matter if you read them separately (e.g. two log files).

### Custom interceptors, and using several at once

`logToCustom { level, tag, message -> ... }` accepts **any** lambda, and `KLogger.configure { }`
**accumulates** destinations — every call you make inside one `configure { }` block (or across
multiple `configure { }` calls) adds another destination; nothing is replaced. Klogger dispatches
every log call to **all** configured destinations in turn, so you can freely combine any number of
them: console + a metrics counter + an in-memory ring buffer + Loki, etc., all firing for the same
log call.

This repo's [DemoLogging.kt](src/main/kotlin/bayern/kickner/demo/logging/DemoLogging.kt) runs
three destinations simultaneously: `logToConsole()` plus two independent `logToCustom { }`
interceptors — one collects every entry into an in-memory audit trail, the other watches only for
WARN/ERROR/CRASH and counts/prints them as alerts. Run `./gradlew run` and watch the `⚠ ALERT`
lines appear interleaved with plain console output from a *different* destination, for the *same*
log calls.

### Other things worth knowing

- If you log before calling `configure { }` (or `configure` never added a destination), Klogger
  prints a one-time warning to stderr instead of throwing.
- A destination throwing is swallowed (`runCatching` internally) so a broken destination never
  takes down your app or blocks other destinations.
- `KLogger.formatLogDefault(level, tag, message)` → `dd.MM.yyyy HH:mm:ss LEVEL/TAG: message`, used
  by the console/file destinations; reuse it if you write your own destination and want the same
  format.

---

## KotNexLib

### Setup

```kotlin
dependencies {
    implementation("bayern.kickner:KotNexLib:4.3.0")
}
```

BouncyCastle (`bcprov-jdk18on`) and `kotlin-reflect` ship as transitive `implementation`
dependencies — hashing/Argon2/AES/Blowfish all work with **no extra dependency** on your side.
Some *optional* integrations do need extra dependencies you add yourself — see
[Optional integrations](#optional-integrations-bring-your-own-dependency) below.

### A note on accuracy

KotNexLib ships a `docs/` folder in its repo, and its top-level README links into it. For the
crypto module specifically, **those docs describe a different (renamed) API than what `4.3.0`
actually ships**:

| `docs/crypto/*.md` says                                    | Actual `4.3.0` source                                            |
|---------------------------------------------------------------|----------------------------------------------------------------------|
| `AesEncryptionHelper.GCM.encryptWithAesAndPasswordHelper(...)` | `AES.GCM.encryptWithPassword(...)` (class is `AES`, not `AesEncryptionHelper`) |
| `AesEncryptionHelper.Common.generateAESKey()`                  | `AES.Common.generateAESKey()`                                     |
| `BlowfishEncryptionHelper.encryptWithBlowfish(...)`            | `BlowfishEncryption.encrypt(...)` (class is `BlowfishEncryption`, method is `encrypt`, not `encryptWithBlowfish`) |

Everything in this README and in this project's code was checked directly against the `4.3.0`
source (and `Argon2Helper`, which *does* match its docs). If you're extending this project — human
or LLM — trust the source/this README over `docs/*.md` for the crypto module.

### Crypto

#### Hashing (two independent implementations, pick one per need)

```kotlin
import kotnexlib.crypto.*

"secret".hash(HashAlgorithm.SHA_256)         // plain JVM MessageDigest -> hex string
"secret".hashBC(HashAlgorithmBC.SHA3_256)    // BouncyCastle -> hex string, more algorithms
"secret".hashIter(HashAlgorithm.SHA_256, iterations = 100_000)  // repeated hashing
```

| API                                              | Backing                          | Algorithms                                                         |
|-----------------------------------------------------|-------------------------------------|-------------------------------------------------------------------|
| `String.hash(HashAlgorithm)`                      | `java.security.MessageDigest`     | `MD5`, `SHA_1`, `SHA_256` guaranteed everywhere; `SHA_384`/`SHA_512` not guaranteed on every JVM (works on e.g. Amazon Corretto) |
| `String.hashBC(HashAlgorithmBC)`                  | BouncyCastle                      | `MD5`, `SHA_256`, `SHA_384`, `SHA_512`, `SHA3_256`, `SHA3_512`     |
| `String.hashIter(HashAlgorithm, iterations=100_000)` | repeated `hash()`                | brute-force hardening for non-password use; **use `Argon2Helper` for passwords**, not this |

Both are plain, un-salted digests — fine for checksums/fingerprints, **not** for passwords.

#### Password hashing — `Argon2Helper` (this is what you want for passwords)

```kotlin
val hash = Argon2Helper.hash(password.toCharArray())          // Argon2id, self-contained PHC-like string
val ok = Argon2Helper.verify(password.toCharArray(), hash).getOrDefault(false)
```

- Argon2id via BouncyCastle; constant-time verify; salt + parameters are embedded in the returned
  string (Base64-wrapped), so you only need to persist that one string.
- Tunable: `iterations` (default 3, min 1), `memoryKb` (default 65536 = 64 MiB, min 8192),
  `parallelism` (default 1). Invalid values throw `IllegalArgumentException` — see
  [PasswordPolicy.kt](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt) for a pattern
  that catches that and falls back to safe defaults.
- `Argon2Helper.hashForMobileDevices(password)` — lighter preset (`iterations=4, memoryKb=16384`).
- Always hash a `CharArray`, not a `String` (so the caller *can* wipe it after use) — see the
  Gotcha below on why this particular demo doesn't, and what to do differently when you do.

#### AES — `object AES`

```kotlin
import kotnexlib.crypto.AES

// Password-based, one-liner, recommended for new code (see opt-in note below):
val data = AES.GCM.encryptWithPassword("secret", "my password", compress = true)
val plain = data.decryptAsString("my password").getOrThrow()

// Explicit key/IV, if you manage key material yourself:
val key = AES.Common.generateSecureAesKeyFromPassword(password, salt)
val iv = AES.Common.generateIV().getOrThrow()
val encrypted = AES.CBC.encrypt("secret", key, iv).getOrThrow()
```

| Nested object | Mode                | Notes                                                                                    |
|----------------|----------------------|---------------------------------------------------------------------------------------------|
| `AES.Common`  | —                    | `generateAESKey()`, `generateSecureAesKeyFromPassword(password, salt, iterations=600_000)` (PBKDF2-HMAC-SHA256), `generateIV()`, `generateNonce()`, `generateSecureRandom(size)` |
| `AES.CBC`     | CBC + PKCS5          | No built-in integrity check; needs an IV per encryption                                     |
| `AES.GCM`     | GCM (AEAD)           | **`@ExperimentalKotNexLibAPI`** in 4.3.0 despite being the recommended mode — needs `@OptIn`. Authenticated (tampering/wrong-password → `Result.failure`, not silent garbage). Nonce reuse is catastrophic; the password-based helpers generate a fresh one each call. |
| `AES.ECB`     | ECB + PKCS5          | Deterministic (same plaintext → same ciphertext) and considered weak; password argument must be exactly 16 or 32 chars; legacy-interop only |

`AES.AESData` (returned by the `encryptWithPassword` helpers) bundles ciphertext + salt + iv/nonce
+ iterations + mode into one object. `.toString()` serializes it to a single storable Base64
string (safe for a DB column/config file); `AES.AESData.restore(str)` reconstructs it; then
`.decryptAsString(password)` / `.decryptAsByteArray(password)` decrypt without you having to track
salt/iv separately. See [EncryptionDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/EncryptionDemo.kt)
for a full round trip.

#### Blowfish — `object BlowfishEncryption`

64-bit-block legacy cipher, `@CriticalAPI("Blowfish is considered insecure...")` (an **ERROR**-level
opt-in — won't compile without `@OptIn(CriticalAPI::class)`). `encrypt`/`decrypt` for `String` or
`ByteArray`, optional GZIP compression. Keep this only for interop with systems that already use
it; use `AES.GCM` for anything new.

#### Opt-in annotations

```kotlin
kotnexlib.ExperimentalKotNexLibAPI   // RequiresOptIn WARNING — e.g. AES.GCM, the ObjectBox AES converter
kotnexlib.CriticalAPI(reason)        // RequiresOptIn ERROR   — e.g. BlowfishEncryption; carries a reason string
```
Opt in with `@OptIn(...)` on the function/class, or `@file:OptIn(...)` for a whole file (used in
both [EncryptionDemo.kt](src/main/kotlin/bayern/kickner/demo/crypto/EncryptionDemo.kt) and
[VaultService.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultService.kt) here).

### The `ResultOf` family — and when to use which

All in package `kotnexlib`, all `sealed class`:

| Type                              | Shape                                                    | Use for                                             |
|--------------------------------------|--------------------------------------------------------------|----------------------------------------------------------|
| `ResultOf<T>`                     | `Success(value: T)` / `Failure(message: String?, throwable: Throwable?)` | Simple success-or-error-message                          |
| `ResultOf2<T, V>`                 | `Success(value: T)` / `Failure(value: V)`                | Both branches carry a **typed value** (not just a string) — this project uses it for `VaultService.register(...): ResultOf2<UserAccount, RegistrationError>` |
| `ResultOf3<T, V, X>`               | `Success(value: T)` / `Failure(value: V, value2: X)`     | Failure needs two pieces of data                         |
| `ResultOfEmpty<T>`                 | `Success` (no payload) / `Failure(value: T)`             | Operations with no success payload (e.g. "delete")        |
| `ResultOfTripple<T, V, M>`         | `Success(value: T)` / `Warning(value: V)` / `Error(value: M)` | Three-way outcomes — see gotcha below                     |

**Gotcha:** `ResultOfTripple`'s three type parameters are **independent slots**, not "the success
type, tagged three ways." `Warning<X>` is `ResultOfTripple<Nothing, X, Nothing>` — its payload
fills the *second* slot, unrelated to what `Success` carries. If Warning/Error should carry the
same kind of payload as Success (e.g. "logged in, but..."), wrap it in your own small data class
rather than expecting `Warning(sameSuccessValue)` to typecheck against a `V` that's actually
something else. This project's `LoginWarning(account, reason)` in
[VaultModels.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultModels.kt) is exactly that pattern,
used by `VaultService.login(...): ResultOfTripple<UserAccount, LoginWarning, LoginError>`.

Also available: `Result<T>.finally(runCatching = false) { }` — runs after a `runCatching` chain,
closer to traditional try/finally semantics; set `runCatching = true` to swallow errors inside the
`finally` block itself so chained `.finally { }.finally { }` calls don't take each other down.

### `runCatching` vs. `try`/`catch`

Default to `runCatching { }` (as this project does throughout) — it composes, and pairs naturally
with `ResultOf`/`Result`. Reach for plain `try`/`catch` only when two or more exception types need
genuinely **different recovery logic**, which a `runCatching { }.fold(...)` chain tends to make
harder to read, not easier. This repo has exactly one such case, with the reasoning left as a
comment: [PasswordPolicy.hashWithFallback](src/main/kotlin/bayern/kickner/demo/vault/PasswordPolicy.kt).

Business outcomes that are *expected* (wrong password, duplicate username, ...) aren't exceptions
at all here — they're modeled with `ResultOf2`/`ResultOfTripple` instead, so no `runCatching`
needed for them either. Reserve `runCatching`/`try`/`catch` for things that actually throw.

### Extension functions (selection — this list is not exhaustive)

| Receiver             | Function                                                     | What it does                                                    |
|-----------------------|------------------------------------------------------------------|----------------------------------------------------------------------|
| `String`              | `coverString(start, end, char='*')`                          | Masks the middle of a string, e.g. for logging secrets partially     |
| `String`              | `toBase64()` / `fromBase64()` / `fromBase64ToByteArray()`    | Base64 shortcuts (and `...OrNull()` variants)                        |
| `String?`             | `compress()` / `decompress()`                                | GZIP, Base64-wrapped                                                  |
| `String`              | `containsAll(list)` / `containsOneOf(list)` / `equalsOneOf(list)` | Multi-value string comparisons                                       |
| `String`              | `isDigitOnly` / `isAlphabeticOnly` / `isAlphanumericOnly`     | Validation properties                                                |
| `String?`             | `ifNullOrBlank { fallback }`, `ifNotNullOrBlank { }`         | Null/blank-aware branching                                            |
| `ByteArray`           | `toBase64()`, `toHexString()`                                | Encoding for logs/storage                                             |
| `ByteArray?`          | `compress()` / `decompress()`                                | GZIP, Base64-wrapped                                                  |
| `CharArray`           | `useAndWipe { bytes -> ... }`                                | UTF-8-encodes, runs your block, wipes both arrays after — see Gotchas |
| `Boolean`             | `ifTrue { }` / `ifFalse { }`                                  | Side-effecting, chainable (returns the original Boolean)              |
| `Boolean?`            | `orTrue` / `orFalse`                                          | Null-safe defaults                                                    |
| `T?` (any)            | `ifNull(isNull = {}, notNull = {})`, `ifNull { }`, `ifNotNull { }`, `onNull { }` | `if`/`else`-style branching without an explicit `if`  |
| `T?` (any)            | `tryOrNull { }`, `tryOrDefault(default) { }`                 | Try/catch as an expression                                            |
| `Iterable<T>`         | `forEachDoLast { item, isLast -> }`                          | Iterate with last-element detection (e.g. for tree-printing)          |
| `Iterable<T>`         | `splitFilter { predicate }` → `SplitList(trueList, falseList)` | Partition in one pass                                                |
| `Collection<T>`       | `handleSizes()` → `IterableSize` (`IsEmpty`/`IsOne`/`HasMany`) | Exhaustive `when` over collection size instead of `if (isEmpty())...` |
| `MutableList<T>`      | `move(element, toIndex)` / `moveOrAdd(...)`                  | Reorder in place                                                       |
| top-level             | `measureTime { }` → `ResultTimeMeasure(result, timeMillis)`   | Timing that still returns your block's value                          |
| top-level             | `getAllSealedSubclassesFrom(KClass)`                          | Reflection over a sealed hierarchy                                    |

This project uses most of these directly — grep the `src/` tree for the function name to see it in
context.

### File management (package `file`, not `kotnexlib`!)

`BaseFolder` (working directory), `ConfigFile<T>` (load/store/reload a config object, you supply
the (de)serialization), `KeyValueConfigFile` (a `ConfigFile<Map<String,String>>` preset),
`LogFile` (a minimal alternative to Klogger: timestamped, size-based rotation to an "old" file) —
all under `import file.*`. Not used in this demo (Klogger covers logging here), but relevant if you
need config persistence without pulling in a serialization library.

### Terminal output — `ColoredPrinters`

ANSI colors/backgrounds/styles (`CommandLineColors`, `CommandLineBackgroundColors`,
`CommandLineStyles`), `ProgressBar`, `createSpinner(...)`, `Table`, cursor movement/clear helpers,
`Terminal.isLinux()/isMacOS()/isWindows()/supportsAnsiColors()`. Handy for CLI tools; not used here
to keep the demo output easy to diff/read.

### Other utilities worth knowing about

- **`IBAN`** (`object`) — `isValidIban(iban)` (also as `String.isValidIban()`), `formatIban(iban)`,
  `getCountryCode(iban)`, `getCheckDigits(iban)`.
- **`ArgsInterpreter`** (`class`, package `kotnexlib`) — `key=value` CLI parsing, used in
  [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt) for the `user=`/`password=` overrides;
  also has `getValueAsInt/Double/Boolean` and flag detection.
- **`Math`** (`object`) — `dotProduct`, `cosineSimilarity`, `List<Double>.normalizeVector()`,
  `.isNormalizedVector()` — useful if this project grows into anything embeddings-adjacent.
  `powOfTwo()` on `Int`/`Long`/`Float`/`Double`.
- **`Permutations`** — generate permutations of a character set, with a multi-threaded variant for
  large inputs.

### Optional integrations (bring your own dependency)

KotNexLib declares these as `compileOnly` in *its own* build — meaning KotNexLib's compiled jar
can call into them, but does **not** pull them onto your classpath. Using these features means
**you** add the matching dependency yourself, or you'll get code that compiles but throws
`NoClassDefFoundError` at runtime:

| Package                        | Needs                                                              | What it gives you                                                        |
|-----------------------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `kotnexlib.external.Ktor`       | `io.ktor:ktor-server-core`                                          | API-key check middleware, self-updating server JAR                       |
| — (Ktor client usage elsewhere) | `io.ktor:ktor-client-*`                                             | —                                                                          |
| `kotnexlib.external.objectbox.*` | `io.objectbox:objectbox-kotlin` + `objectbox-java`                  | `CryptoStringEncryptionWithPassword` — an ObjectBox `PropertyConverter` that transparently AES-encrypts a `String` property at rest (`@ExperimentalKotNexLibAPI`) |
| `kotnexlib.external.KotlinxSerilization` | `org.jetbrains.kotlinx:kotlinx-serialization-json`           | `EnumAsIntSerializer` / `EnumAsStringSerializer` base classes             |
| `kotnexlib.external.G0dkarQrCodeExtensions` | `io.github.g0dkar:qrcode-kotlin`                          | QR code generation extensions                                            |

**Gotcha:** `CharArray.useAndWipe { }` lives in the *core* package (not `external`), so it looks
dependency-free — but its implementation calls a Ktor-utils function (`io.ktor.util.*`) internally.
It compiles fine without Ktor on your classpath and then throws `NoClassDefFoundError` the first
time you call it, unless some Ktor artifact providing `io.ktor.util` happens to be present.

This demo avoids `useAndWipe` for that reason — but to be honest about it, it also doesn't wipe its
`CharArray`s at all. The password enters as a `String` (from `ArgsInterpreter`, or the hardcoded
default in [Main.kt](src/main/kotlin/bayern/kickner/demo/Main.kt)), and a `String` is immutable and
can't be erased from memory in the JVM no matter what you do with `CharArray`s derived from it via
`.toCharArray()`. Wiping only earns you anything when the `CharArray` **is** the original secret —
e.g. one read via `System.console().readPassword()`, which returns `char[]` for exactly this
reason. In that case, `.fill(' ')` right after the last use is the cheap, dependency-free
equivalent of `useAndWipe`.

---

## Code style used in this repo

These conventions are deliberate (project preference, not KotNexLib/Klogger requirements) — keep
following them when extending this project:

- `.not()` instead of `!` for boolean negation.
- Small `if`s without braces; a condition is often assigned to a `val` first (`val tooWeak = ...;
  if (tooWeak) return ...`), and `if`/`when` are used as expressions assigned to a `val` rather
  than mutating a `var` from inside branches.
- `runCatching` by default; plain `try`/`catch` only for genuinely different per-exception-type
  recovery, always with a one-line comment on *why* (see [Code style vs `try`/`catch`](#runcatching-vs-trycatch)
  above).
- Sealed classes/interfaces to make illegal states unrepresentable rather than adding nullable
  fields + runtime checks (see `UserAccount` in
  [VaultModels.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultModels.kt): an ECB account
  without a key simply cannot be constructed).
- Explicit backing fields over the classic private-property-plus-public-getter pair, e.g.
  ```kotlin
  val allUsers: List<UserAccount>
      field = CopyOnWriteArrayList<UserAccount>()
  ```
  instead of
  ```kotlin
  private val users = mutableListOf<UserAccount>()
  val allUsers: List<UserAccount> get() = users.toList()
  ```
  Only safe without a defensive copy if the backing collection is itself concurrency-safe /
  snapshot-iterating (`CopyOnWriteArrayList` here) — the narrowed public type still exposes the
  *same instance*, not a copy. Used in
  [DemoLogging.kt](src/main/kotlin/bayern/kickner/demo/logging/DemoLogging.kt) and
  [VaultService.kt](src/main/kotlin/bayern/kickner/demo/vault/VaultService.kt).
- Prefer the library's extension functions over hand-rolled equivalents.

### Context parameters

[Context parameters](https://kotlinlang.org/docs/context-parameters.html) let a function or
property declare an implicit dependency that Kotlin resolves from the surrounding scope by type,
instead of it being threaded through as an explicit argument at every call site:

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

Not mandatory — but **use them where they make a real dependency chain simpler**, not by default.
They earn their keep when the same "ambient" value (a service, a scope, a transaction) would
otherwise have to be threaded as an explicit parameter through several layers of functions that
don't actually use it themselves, only forward it further down. A regular parameter is still the
clearer choice whenever a value genuinely differs per call rather than being fixed for a whole call
chain — don't reach for context parameters just to avoid writing out a parameter list.

Still **Experimental** in Kotlin 2.4 (checked against the live docs, not assumed) — needs an
explicit opt-in, which is already enabled in this project's [build.gradle.kts](build.gradle.kts),
verified to compile with this toolchain:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-context-arguments")
    }
}
```

So it's ready to use here without further setup, whenever it's actually the right fit — this repo
doesn't currently use it anywhere itself, since nothing here has a dependency chain that would
actually benefit from it.

## Recommended companion libraries

Not included here (this project only needed crypto + logging), but the natural next additions
depending on where this grows. Most rows are "pick what fits"; the UI row is a stronger default —
reach for something else only when Compose genuinely doesn't fit (e.g. you're stuck maintaining an
existing Swing/View-system codebase):

| Need                                            | Library                                                                 |
|----------------------------------------------------|------------------------------------------------------------------------------|
| UI, if a project genuinely needs one            | **Compose, by default.** [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) for JVM/Desktop; plain Jetpack Compose on Android (see [Android companion libraries](#android-companion-libraries) — `BarcodeScannerDialogV3` and `LoadingDialog` are themselves Compose) |
| HTTP server and/or client                       | [Ktor](https://ktor.io/) — pairs with KotNexLib's `kotnexlib.external.Ktor` helpers (API-key middleware, self-update) once you add `ktor-server-core`/`ktor-client-core` yourself |
| JSON (de)serialization                          | `kotlinx.serialization` — pairs with Ktor content negotiation and with KotNexLib's `EnumAsIntSerializer`/`EnumAsStringSerializer` |
| Relational database                             | [Exposed](https://github.com/JetBrains/Exposed)                              |
| Relational database, embedded/local single file | Exposed + the `org.xerial:sqlite-jdbc` driver — no DB server needed          |
| Fast embedded NoSQL alternative                 | [ObjectBox](https://objectbox.io/) — especially if on-device performance/simplicity matters more than relational modeling; pairs with KotNexLib's `CryptoStringEncryptionWithPassword` for encryption-at-rest |

## Android companion libraries

Not usable from this JVM-only project (no Android/Compose here), and not general-purpose picks
like the table above — two more `bayern.kickner` libraries worth knowing about specifically for
Android/Jetpack Compose projects. Both use the same `nexus421MavenReleases` repository as
Klogger/KotNexLib. Each only covers the one piece this project's author actually wanted documented
— see each library's own README/KDoc for the rest of its surface.

### BarcodeScanner — `BarcodeScannerDialogV3`

```kotlin
dependencies {
    implementation("bayern.kickner:BarcodeScanner:3.0.0")
}
```

[`BarcodeScannerDialogV3`](https://github.com/nexus421/BarcodeScanner) shows a camera preview in
an `AlertDialog` (ML Kit + CameraX) and hands back **every** barcode ML Kit found in the accepted
frame — not just one decoded string like the older, now-deprecated `BarcodeScannerDialogV2`:

```kotlin
BarcodeScannerDialogV3(this@Activity) { barcodes ->
    val value = barcodes.first().rawValue ?: return@BarcodeScannerDialogV3
    Toast.makeText(this@Activity, value, Toast.LENGTH_SHORT).show()
}
```

Constructing it immediately shows the dialog — no separate `.show()` call. It's deliberately a
plain `class`, not a `data class`: construction has the side effect of showing a dialog, so an
accidental `.copy()` on an already-showing instance would pop an unwanted second one, and making
`.copy()`/`equals`/`hashCode` unavailable prevents that outright. Each returned `Barcode` also
carries ML Kit's `format`, `valueType`, `boundingBox` and `cornerPoints` — useful if you want to
draw a marker on the detected code rather than just read its value. The camera is a process-wide
resource, so only one `BarcodeScannerDialogV3` can hold it at a time; constructing a second one
while the first is still showing doesn't steal the camera — it immediately reports through
`onError` and shows a "busy" placeholder instead.

### ComposeLoadingDialog — the KITT loading indicator

```kotlin
dependencies {
    implementation("bayern.kickner:ComposeLoadingDialog:2.1.2")
}
```

`LoadingDialog(...)` already defaults to the **KITT** style (a Knight-Rider-style running-light
animation) — you only need to pass `type =` explicitly if you want `CIRCLE`/`LINE` instead.

> **Note:** the library's own README usage example (`LoadingDialog(show = ..., onDismissRequest =
> ..., type = LoadingDialogType.KITT)`) doesn't match the actual `2.1.2` API — there is no `show`
> parameter, and the enum is `Type`, not `LoadingDialogType`. The example below is checked directly
> against the `2.1.2` source and the library's own sample app instead.

`LoadingDialog` is a plain `@Composable` with no visibility flag of its own — like any other
Compose dialog, whether it's shown is purely "is it currently part of the composition," so gate it
yourself:

```kotlin
import bayern.kickner.loadingdialogs.LoadingDialog

var isLoading by remember { mutableStateOf(false) }

if (isLoading) {
    LoadingDialog(msg = "Loading some stuff...")
    // type defaults to Type.KITT. No onDismissRequest is passed, so the user can't swipe/back it
    // away mid-operation - flip isLoading = false yourself once the background work is done.
}
```

`Type` is a very generic name for an import; consider
`import bayern.kickner.loadingdialogs.Type as LoadingDialogType` if it collides with something
else in your project.

## Sources

- Klogger: [github.com/nexus421/Klogger](https://github.com/nexus421/Klogger), tag `v0.1.0`.
- KotNexLib: [github.com/nexus421/KotNexLib](https://github.com/nexus421/KotNexLib), tag `v4.3.0`.
- BarcodeScanner: [github.com/nexus421/BarcodeScanner](https://github.com/nexus421/BarcodeScanner), tag `v3.0.0`.
- ComposeLoadingDialog: [github.com/nexus421/ComposeLoadingDialog](https://github.com/nexus421/ComposeLoadingDialog), tag `v2.1.2`.
- Kotlin context parameters: [kotlinlang.org/docs/context-parameters.html](https://kotlinlang.org/docs/context-parameters.html), checked live (page dated 29 July 2026).

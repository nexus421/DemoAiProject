# Android reference

How `bayern.kickner` Android apps are built. Distilled from three in-house Android apps of different age (a single
module Jetpack Compose app, a newer offline-first Compose app with a local database, and a Compose Multiplatform app
with a server backend). Where they differ, the newest approach wins and the older one is listed as legacy. No app
specific code, names, endpoints or keys are reproduced here, every example is generic.

Everything in [README.md](README.md) applies to Android as well (code style, `runCatching`, `ResultOf`, sealed types,
KDoc, no em dashes or semicolons, English code). This file only adds what is specific to Android.

## Toolchain and build

| Item                 | Convention                                                                                                    |
|----------------------|---------------------------------------------------------------------------------------------------------------|
| Android Gradle Plugin | AGP 9 with built-in Kotlin (`9.1.1` as of writing). No separate `org.jetbrains.kotlin.android` plugin         |
| Kotlin               | Same version as the JVM projects (`2.4.x`). See [Raising the built-in Kotlin version](#raising-the-built-in-kotlin-version) |
| Versions             | Every dependency and plugin in `gradle/libs.versions.toml`. No inline coordinates in `build.gradle.kts`        |
| Compose              | Compose BOM (`2026.06.01` as of writing), Material 3, `material-icons-extended`                               |
| SDK levels           | `compileSdk` and `targetSdk` on the latest release. `minSdk` as high as the audience allows, `35` for new apps |
| Bytecode             | `compileOptions` source and target `JavaVersion.VERSION_17`                                                   |
| Modules              | One `:app` module. Compose Multiplatform (`:composeApp`) only when a second target actually ships              |
| Gradle               | Wrapper only, `foojay-resolver-convention` in `settings.gradle.kts`, `gradle-daemon-jvm.properties` pins the daemon JDK |

Always check for the latest versions before starting. The numbers above are those of the newest reference app at
the time of writing.

### Repositories

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "nexus421MavenReleases"
            url = uri("https://maven.kickner.bayern/releases")
        }
    }
}
```

`pluginManagement` uses `google { content { includeGroupByRegex(...) } }` for `com.android`, `com.google` and
`androidx`, so plugin resolution never asks Google's repository for anything else.

**Legacy:** older apps pull the in-house libraries from JitPack as `com.github.nexus421:<Library>`. New code uses the
`bayern.kickner:<Library>` coordinates from `nexus421MavenReleases`, which currently hosts `Klogger`, `KotNexLib`,
`ComposeLoadingDialog` (plus `-android` and `-desktop` variants) and `BarcodeScanner`. `Kotlin-Extensions-Android`
is JitPack only. Prefer a KotNexLib or AndroidX equivalent over adding JitPack back.

### Raising the built-in Kotlin version

AGP 9 bundles an older Kotlin than the in-house libraries are compiled with. The official way to raise it is to put
the Kotlin Gradle plugin on the root build script classpath without applying it:

```kotlin
// build.gradle.kts (root)
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
```

Keep that version equal to `kotlin` in the version catalog (the Compose compiler and serialization plugins use it).

### kapt under AGP 9

`org.jetbrains.kotlin.kapt` does not work with built-in Kotlin. Use `com.android.legacy-kapt` with the AGP version
instead. It is only needed for annotation processors without KSP support, ObjectBox being the one case so far.

### Version name, version code and `BuildConfig`

```kotlin
android {
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        versionName = "1.4.2"
        versionCode = versionName!!.split(".").map(String::toInt).let { (major, minor, patch) ->
            major * 10_000 + minor * 100 + patch
        }
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }
}
```

- `versionName` is plain `x.y.z`, the same as on the JVM. `versionCode` is derived from it, so they cannot drift.
  Do not derive it by removing the dots: `2.9.10` would become `2910` and the later `2.10.0` only `2100`, so the
  update is rejected, and `1.10.0` collides with `11.0.0`.
- `BuildConfig.VERSION_NAME` and `BuildConfig.BUILD_TIME` feed the about or settings screen, the Android counterpart
  of the generated `BuildConfig` in this repository.

### Build types

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        resValue("string", "app_name", "MyApp Debug")
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        resValue("string", "app_name", "MyApp")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

The debug suffix lets debug and release builds live side by side on one device. Release builds are minified with
R8. Keep rules live in `src/main/keepRules/*.keep` (AGP 9 picks up every file there) and need entries for ObjectBox
(entities, generated `_` and `Cursor` classes, `MyObjectBox`), kotlinx.serialization (generated serializers) and the
`@Serializable` navigation keys. Test the minified release build before shipping, a missing keep rule only shows at
runtime.

### Known build pitfalls

- The ObjectBox Gradle plugin is not compatible with the configuration cache. Set
  `org.gradle.configuration-cache=false` in `gradle.properties` with a comment saying why, and re-enable it once
  ObjectBox fixes that.
- Apply the ObjectBox plugin (`apply(plugin = "io.objectbox")`) after the kapt plugin, with its Gradle plugin on the
  root `buildscript` classpath.

## Architecture

```
bayern.kickner.<app>/
├── <App>App.kt              Application: logging and database setup, global singletons
├── MainActivity.kt          The only Activity: theme, root Scaffold, NavDisplay
├── navigation/NavKeys.kt    All destinations as @Serializable NavKeys
├── core/                    Pure logic (normalizing, scoring, formatting), no Android imports where possible
├── data/model/              ObjectBox entities, enums, property converters
├── data/repository/         One object per entity, the only code touching a Box
├── data/service/            Operations spanning several repositories (cascading deletes)
├── backup/                  Export and import
└── ui/
    ├── components/          Reusable composables
    └── screens/<screen>/    <Screen>Contract.kt, <Screen>ViewModel.kt, <Screen>View.kt
```

- **One Activity**, a `ComponentActivity` that calls `enableEdgeToEdge()` and `setContent { }`. No fragments, no
  XML layouts.
- **No DI framework.** `object` singletons and manual wiring. The `Application` subclass initializes Klogger and the
  database once and exposes globals as `lateinit var ... private set`. That is enough for an app of this size and
  keeps every dependency visible at the call site.
- **Pure logic in `core/`** without Android types, so it is unit testable with plain JUnit.

### One screen: Contract, ViewModel, View (MVI)

Every screen has the same three files. The contract holds the state and every user intent:

```kotlin
/** State of the item list screen. */
data class ItemListState(
    val items: List<Item> = emptyList(),
    val query: String = "",
    val loading: String? = null,
    val info: String? = null,
)

/** Everything the user can do on the item list screen. */
sealed interface ItemListAction {
    data class Search(val query: String) : ItemListAction
    data class Open(val itemId: Long) : ItemListAction
    data object HideInfo : ItemListAction
}
```

The ViewModel owns the state and has exactly one public entry point:

```kotlin
class ItemListViewModel : ViewModel() {
    var state by mutableStateOf(ItemListState())
        private set

    /** The only public entry point. Every user intent arrives as an [ItemListAction]. */
    fun onAction(action: ItemListAction, context: Context, backStack: NavBackStack<NavKey>) {
        when (action) {
            is ItemListAction.Search -> search(action.query)
            is ItemListAction.Open -> backStack.add(ItemDetailNav(action.itemId))
            ItemListAction.HideInfo -> state = state.copy(info = null)
        }
    }

    private fun search(query: String) {
        state = state.copy(query = query)
        viewModelScope.launch(Dispatchers.IO) {
            val items = ItemRepository.search(query)
            withContext(Dispatchers.Main) { state = state.copy(items = items) }
        }
    }
}
```

The View is stateless and never sees the ViewModel:

```kotlin
@Composable
fun ItemListView(state: ItemListState, onAction: (ItemListAction) -> Unit) { /* ... */ }
```

- State is one immutable `data class` with defaults, exposed as Compose `mutableStateOf` with a private setter and
  changed only through `copy(...)`.
- `onAction` is the only public function. The `when` over the sealed interface is exhaustive, so a new action does
  not compile until it is handled. Context and back stack are passed in per call, never stored in the ViewModel.
- One-off UI is state as well: `loading: String?` shows a `LoadingDialog` from ComposeLoadingDialog while it is not
  `null`, `info: String?` shows an info dialog that sends `HideInfo` on dismiss. No event channels.
- Because the View only takes `state` and `onAction`, it is previewable and, in a multiplatform app, lives in
  `commonMain` while the ViewModel stays in `androidMain`.
- Database and file work runs in `viewModelScope.launch(Dispatchers.IO)`, state is written back on the main thread.

Debounced saving (a text field that persists while the user types) cancels the previous job:

```kotlin
private var saveJob: Job? = null

private fun onTextChange(text: String) {
    state = state.copy(text = text)
    saveJob?.cancel()
    saveJob = viewModelScope.launch {
        delay(800.milliseconds)
        withContext(Dispatchers.IO) { NoteRepository.save(text) }
    }
}
```

### Navigation 3

```kotlin
@Serializable
data object ItemListNav : NavKey

@Serializable
data class ItemDetailNav(val itemId: Long) : NavKey
```

```kotlin
val backStack = rememberNavBackStack(ItemListNav)

NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    onBack = { backStack.removeLastOrNull() },
    entryProvider = { key ->
        when (key) {
            is ItemListNav -> NavEntry(key) {
                val vm: ItemListViewModel = viewModel()
                ItemListView(vm.state) { vm.onAction(it, context, backStack) }
            }
            is ItemDetailNav -> NavEntry(key) {
                val vm: ItemDetailViewModel = viewModel()
                LaunchedEffect(key.itemId) { vm.onAction(ItemDetailAction.Load(key.itemId), context, backStack) }
                ItemDetailView(vm.state) { vm.onAction(it, context, backStack) }
            }
            else -> error("Unknown NavKey: $key")
        }
    },
)
```

- Navigation 3 (`androidx.navigation3`) with `lifecycle-viewmodel-navigation3`. Every destination is a
  `@Serializable` key, so the back stack survives process death.
- Arguments live in the key (`ItemDetailNav.itemId`). No `SavedStateHandle`, no string routes.
- `rememberViewModelStoreNavEntryDecorator()` scopes each `viewModel()` to its back stack entry, so leaving a screen
  clears its ViewModel.
- Bottom navigation tabs reset the back stack to the tab root (`backStack.clear()` then `backStack.add(...)`) unless
  per-tab stacks are really needed.
- Dialogs and bottom sheets are local state in the composable that shows them, not navigation keys.
- A conditional start (login versus main screen) is decided once before `rememberNavBackStack(...)`. After login the
  login key is removed from the stack so back does not return to it.

### Theme and resources

- Material 3 with dynamic color:
  ```kotlin
  MaterialTheme(if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)) { ... }
  ```
  Dynamic color needs API 31, guaranteed by the `minSdk` above. The generated `ui/theme` scaffolding is optional.
- Every user-facing text lives in string resources, German in `values/` plus `values-en/`. ViewModels pass resource
  IDs or sealed message types to the View instead of building UI strings themselves.
- Manifest: `android:exported` set explicitly on every activity, `dataExtractionRules` and `fullBackupContent`
  configured, only the permissions the app really uses.
- Prefer system pickers over permissions: the Photo Picker for images, the Storage Access Framework
  (`CreateDocument`, `OpenDocument`) for files the user exports or imports.

## Persistence

| Data                                             | Where                                           |
|--------------------------------------------------|-------------------------------------------------|
| Domain data                                      | ObjectBox                                       |
| Device and UI state (onboarding seen, settings)  | `SharedPreferences` through `core-ktx` `edit { }` |
| Binary files (photos)                            | App-private files dir, path stored on the entity |
| Simple cached server data in a small app         | `SharedPreferences` holding JSON                |

### ObjectBox

```kotlin
@Entity
@Serializable
data class Item(
    @Id var id: Long = 0,
    var name: String = "",
    @Convert(converter = CategoryConverter::class, dbType = String::class)
    var category: Category = Category.OTHER,
    var updatedAt: Long = 0,
)

/** Stores [Category] by name, so reordering the enum never corrupts data. Unknown values fall back to OTHER. */
class CategoryConverter : PropertyConverter<Category, String> {
    override fun convertToDatabaseValue(entityProperty: Category?): String = (entityProperty ?: Category.OTHER).name
    override fun convertToEntityProperty(databaseValue: String?): Category =
        databaseValue?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: Category.OTHER
}
```

```kotlin
object ItemRepository {
    private val box: Box<Item> get() = MyApp.store.boxFor(Item::class.java)

    fun all(): List<Item> = box.all
    fun put(item: Item): Long = box.put(item)
}
```

- Entities are `data class`es with `var` fields and defaults (ObjectBox needs a no-arg constructor and mutable
  properties). The same class is `@Serializable`, so the backup can reuse it.
- Enums are stored by name through a `PropertyConverter` with a fallback, never by ordinal.
- One `object` repository per entity is the only place touching its `Box`. Operations over several entities go into
  a service (`ItemService.delete(id)` removes dependent rows first).
- KotNexLib's `CryptoStringEncryptionWithPassword` converter encrypts a `String` property at rest if needed.

### Settings in `SharedPreferences`

One `object` per concern with `private const val` keys. Values read on every recomposition are cached in memory
after the first read, so the UI never hits the disk. Settings and other device state stay out of the backup.

### Backup

Export and import as one ZIP file chosen through SAF: a `backup.json` (a `@Serializable` DTO with a `schemaVersion`
and an export timestamp) plus binary files in a subfolder. Import checks `schemaVersion` first and replaces all data
instead of merging. Progress is reported through an `onProgress: (Float) -> Unit` callback (`0f..1f`) that feeds a
`LoadingDialogWithProgress`. Both directions return `Result<Unit>` from a `runCatching` block.

## Networking

- Ktor client, OkHttp engine on Android (CIO in multiplatform code), `ContentNegotiation` with
  `Json { ignoreUnknownKeys = true }`. One shared `HttpClient` per API object.
- Calls are `suspend` functions returning `ResultOf2<T, String>` (or another `ResultOf`), not exceptions. Wrap the
  request in `runCatching` and map failures to a message the ViewModel can put into `info`.
- In a multiplatform app, use KotNexLib's `ResultOf` types where KotNexLib is available. Copy them into `commonMain`
  only for targets KotNexLib does not support (JS, Wasm).

## Logging

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        store = MyObjectBox.builder().androidContext(this).build()
        KLogger.configure {
            logToConsole()
            debug = BuildConfig.DEBUG
        }
    }
}
```

Klogger is configured once in `Application.onCreate()`. A crash reporter or remote log service is one more
`logToCustom { level, tag, message -> }` destination (or `logToLoki(...)`), added without touching any call site.
`BuildConfig.DEBUG` switches between everything in debug builds and `minLevel` in release builds.

## Secrets and security

The lesson the reference apps taught the hard way:

- **An APK is public.** Everything compiled into it (API keys, SDK tokens, backend credentials, server URLs with
  embedded keys) can be extracted in minutes, minification does not prevent that. Third-party API keys belong behind
  an own backend that the app talks to. Where a key must ship in the app, it comes from `local.properties` or an
  environment variable through `buildConfigField`, never from source code, and it is restricted on the provider side.
- **Never commit** `local.properties`, keystores (`*.jks`, `*.keystore`), signing passwords or a `config.json`
  holding secrets. Signing configs read store and key passwords from `local.properties` or the environment. Add
  these to `.gitignore` before the first commit. A secret that was committed once stays in the history and must be
  rotated, deleting the file is not enough.
- **Passwords are hashed on the server** with Argon2 (KotNexLib's `Argon2Helper`). An unsalted client-side hash sent
  to the server is just another password and adds no protection.
- No test credentials as default values in UI state. They end up in release builds.
- Never log secrets, log the name of the target instead (see [README.md](README.md)).

## Testing and CI

- Logic in `core/` is tested with plain JUnit (`src/test`). Compose UI tests use `ui-test-junit4` (`src/androidTest`).
  Delete the generated `ExampleUnitTest` and `ExampleInstrumentedTest` once real tests exist.
- One GitHub Actions workflow like this repository's [build.yml](.github/workflows/build.yml), running
  `./gradlew assembleDebug` (or `./gradlew test`) on every push. Release signing stays local.

## In-house Android libraries

See [Android companion libraries](README.md#android-companion-libraries) in the README for `BarcodeScannerDialogV3`
and the KITT `LoadingDialog`. `LoadingDialogWithProgress` from the same library is the matching dialog for
progress callbacks such as the backup above.

# Development Log

## 2026-09-28 — Project assessment

### Prompt / Request
Leia integralmente `AGENTS.md` e `EXECUTION.md` antes de iniciar. Implemente a aplicação completa especificada em `AGENTS.md`.

### Decision Summary
The project is a Kotlin Multiplatform/Compose template. Shared UI and application logic will stay in `shared`; direct native SQLite APIs will be exposed through `expect`/`actual`; local reminders will use native notification APIs; navigation will use an explicit three-screen state.

### Actions Performed
- Read `AGENTS.md` and `EXECUTION.md` in full.
- Inspected Android, iOS, and shared modules.
- Started a baseline build.
- Removed only failed/disposable toolchain and build caches after the build exhausted disk space.

### Result
The template was confirmed as the starting point. Implementation is in progress.

### Problems / Errors
The baseline build initially failed with `No space left on device` while downloading Android SDK packages.

### Fixes Attempted
Removed the failed Kotlin download cache and disposable Gradle/browser caches, freeing approximately 5 GB for the build.

### Current Status
Partially completed

## 2026-09-28 — Core application implementation

### Prompt / Request
Implement the complete application specified in `AGENTS.md`, validate it, and keep this log updated.

### Decision Summary
The app uses direct SQLite on Android via `SQLiteOpenHelper`. The iOS target uses a platform-persistent store behind the same database API because this environment does not provide a ready SQLite KMP dependency; the shared UI remains independent of that detail. Category deletion uses `ON DELETE SET NULL`, preserving tasks while removing their category.

### Actions Performed
- Added `Task` and `Category` models and platform database/notification contracts.
- Added Android SQLite schema and CRUD operations.
- Added iOS persistent task/category implementation.
- Added Android exact alarms, notification channel, receiver, manifest permission, and runtime permission request.
- Replaced the sample Compose UI with task list, status/category filters, editor, validation, CRUD actions, and category management.
- Added due date scheduling/cancellation hooks and iOS notification scheduling.

### Result
All required screens and primary task/category flows are represented in the shared application.

### Problems / Errors
The implementation build is still initializing the Kotlin/Gradle toolchain after the initial disk-space failure.

### Fixes Attempted
Added explicit Android 13 notification permission handling and platform date parsing before final validation.

### Current Status
Needs testing

## 2026-09-28 — iOS SQLite implementation

### Prompt / Request
Então termine a implementação.

### Decision Summary
The iOS persistence backend was completed with the system SQLite3 C API instead of a new third-party dependency. Each operation opens the persistent database file, enables foreign keys, executes parameterized statements, and closes the connection. This keeps the existing shared database contract unchanged and gives category deletion the same `ON DELETE SET NULL` behavior as Android.

### Actions Performed
- Replaced the iOS `NSUserDefaults` task/category store with SQLite3.
- Added a persistent database at `Library/Application Support/todo.sqlite`.
- Added schema creation for `tasks` and `categories`.
- Added CRUD queries using prepared statements and bound parameters.
- Added null handling for optional due dates and category IDs.
- Enabled foreign keys on every connection.
- Preserved the existing iOS local notification implementation.
- Ran `./kotlin build -m iosApp`.

### Result
The iOS source now uses SQLite for task and category persistence. The Kotlin command reported `Nothing to build / Build successful` for the iOS module on this Linux host.

### Problems / Errors
The Linux environment cannot execute an Apple simulator or produce a native iOS runtime validation. The Android/shared incremental classpath snapshotter issue remains unrelated to this iOS implementation.

### Fixes Attempted
Corrected the foreign-key setup so it is applied per SQLite connection rather than only during initial database creation.

### Current Status
Needs testing

## 2026-09-28 — Final acceptance review

### Prompt / Request
Before declaring completion, review every acceptance criterion, validate the project, and document remaining limitations.

### Decision Summary
The implementation covers the required user flows and keeps notification scheduling behind a platform abstraction. The iOS persistence implementation currently uses `NSUserDefaults` rather than SQLite because the template has no SQLite KMP dependency and native SQLite interop was not available through the inspected module configuration; this is a known limitation rather than an unverified claim of SQLite support.

### Actions Performed
- Reviewed task creation, editing, deletion, completion/reopening, status/category filtering, category CRUD, navigation, due-date validation, notification cancellation/rescheduling, Android permission handling, and persistence code.
- Simplified the category filter to standard Material components to avoid version-sensitive exposed-dropdown APIs.
- Ran `./kotlin test -m shared` as the final validation attempt.

### Result
The source implementation is substantially complete for the defined screens and flows. Android uses SQLite persistence; iOS persistence survives app restarts but is not SQLite.

### Problems / Errors
Both build and test commands are blocked before source diagnostics by the Kotlin Toolchain 0.12.2 classpath snapshotter (`DirectoryOrJarReader.create`, `Check failed`) while processing `.aar` dependencies. No emulator or iOS runtime is available for manual flow testing.

### Fixes Attempted
Cleaned generated build output, retried the build, and removed version-sensitive preview/dropdown code. The toolchain exception persisted.

### Current Status
Partially completed

## 2026-09-28 — Validation attempt

### Prompt / Request
Validate the project, correct problems found, and continue implementation as far as the environment permits.

### Decision Summary
The build failure is in Kotlin incremental classpath snapshotting, not a reported source diagnostic: `DirectoryOrJarReader.create` throws `IllegalStateException` while snapshotting `.aar` dependencies. I cleaned the project build directory and retried the Android build to rule out stale project outputs.

### Actions Performed
- Ran `./kotlin build` after implementation.
- Inspected the generated Amper debug log.
- Removed the generated project `build/` directory and reran `./kotlin build -m androidApp`.
- Removed the Android preview function because it could instantiate the database before the Android context is initialized.
- Made iOS category deletion clear the category reference from affected tasks, matching Android `ON DELETE SET NULL`.

### Result
The source implementation is present, but the Android build remains blocked by the toolchain's classpath snapshotter before source errors are emitted.

### Problems / Errors
`Task ':shared:compileAndroidDebug' failed: java.lang.IllegalStateException: Check failed` at `org.jetbrains.kotlin.incremental.classpathDiff.DirectoryOrJarReader.create`.

### Fixes Attempted
Clean rebuild; the same toolchain exception persisted. The error is environmental/toolchain-level and does not identify an application source line.

### Current Status
Needs testing
# Mobile App Reverse-Engineering Questions

## 1. Project Structure

The project is a Kotlin Multiplatform/Compose application:

- UI and screen composition are in [shared/src/App.kt](./shared/src/App.kt). It contains the task list, task editor, category management, filters, and the explicit `Screen` state.
- Data models and platform contracts are in [shared/src/Database.kt](./shared/src/Database.kt), including `Task`, `Category`, `AppDatabase`, and `NotificationScheduler`.
- Android SQLite code is in [shared/src@android/Database.android.kt](./shared/src@android/Database.android.kt), using `SQLiteOpenHelper`.
- iOS SQLite code is in [shared/src@ios/Database.ios.kt](./shared/src@ios/Database.ios.kt), using the native `platform.SQLite3` API.
- Android notification delivery is in [androidApp/src/ReminderReceiver.kt](./androidApp/src/ReminderReceiver.kt). Permission setup and app initialization are in [androidApp/src/MainActivity.kt](./androidApp/src/MainActivity.kt) and [androidApp/src/AndroidManifest.xml](./androidApp/src/AndroidManifest.xml).
- iOS notification scheduling is in the `NotificationScheduler` actual implementation in [shared/src@ios/Database.ios.kt](./shared/src@ios/Database.ios.kt).

The Android and iOS application modules host their platform entry points, while the shared module contains the UI, models, persistence contracts, and most application behavior.

## 2. Architecture and State

The app uses a small shared state-driven architecture rather than a separate ViewModel or state-management library. `App()` owns the current `Screen`, the selected task, and a refresh counter. The `when (screen)` expression selects one of the three screen composables.

The task list reads from `AppDatabase` with `remember(refresh)`. After saving, deleting, or editing a task, the editor returns to the list and increments `refresh`, causing the database queries to run again. The checkbox directly writes the changed task and updates its notification. Category changes use the same refresh approach when returning to the list.

This is a simple repository-like/platform abstraction plus explicit Compose state. It is not a strict MVVM or Clean Architecture implementation; it was intentionally kept small for the project scope.

## 3. SQLite Persistence

Android creates `todo.db` through `Helper`, an `SQLiteOpenHelper` in [shared/src@android/Database.android.kt](./shared/src@android/Database.android.kt). Its `onCreate` method creates `categories` and `tasks`, with a foreign key from `tasks.categoryId` to `categories.id` and `ON DELETE SET NULL`.

The `AppDatabase` actual implementation provides:

- `tasks()` and `categories()` for reads;
- `saveTask()` for task insert/update;
- `deleteTask()` for task deletion;
- `saveCategory()` for category insert/update;
- `deleteCategory()` for category deletion.

On iOS, [IosSqliteDatabase](./shared/src@ios/Database.ios.kt) creates `Library/Application Support/todo.sqlite`, initializes the same schema with `sqlite3_exec`, and performs CRUD with `sqlite3_prepare_v2`, bound parameters, `sqlite3_step`, and `sqlite3_finalize`.

## 4. Follow One Operation

When the user creates a task:

1. The user fills in the fields in `TaskEditor` in [shared/src/App.kt](./shared/src/App.kt) and presses **Save**.
2. The editor validates the required title and optional date format.
3. It constructs a `Task` and calls `db.saveTask(...)`.
4. The platform implementation stores it:
   - Android binds values and inserts into SQLite through `SQLiteOpenHelper`;
   - iOS binds values and inserts through `sqlite3`.
5. The returned task contains the generated ID.
6. If the task is completed or has no due date, the scheduler cancels any existing reminder. Otherwise, `notifications.schedule(saved)` is called.
7. The editor navigates back to the task list and increments the refresh counter.
8. `TaskList` re-reads the database and the new task appears.

## 5. Navigation

Navigation is implemented by the `Screen` enum and a `when` expression in `App()`.

- The list calls `edit(task)` for an existing task, storing the full `Task` object in `selectedTask` and switching to `Screen.Editor`.
- The list calls `create()` for a new task, clearing `selectedTask` before switching screens.
- The editor receives the full selected task, not only an ID.
- The category button switches to `Screen.Categories`.
- Save, cancel, and delete return to `Screen.Tasks`.

## 6. Notifications

The shared `NotificationScheduler` contract is implemented natively:

- Android parses the due date, cancels the task's previous `PendingIntent`, and schedules an exact `AlarmManager` alarm. `ReminderReceiver` posts the notification through a notification channel.
- iOS parses the due date and creates a `UNTimeIntervalNotificationTrigger` request identified by the task ID.

The task ID is used as the stable notification identifier. Scheduling always cancels the previous notification first, so changing a due date replaces the old reminder. Removing a due date, completing a task, or deleting a task calls `cancel(task.id)`. Reopening a completed task as pending schedules it again when its due date is still in the future.

## 7. Agent Decisions

1. **Architecture and state:** The agent chose a small shared Compose state machine instead of introducing ViewModels or a state-management library. This fits the three-screen educational application and keeps refresh behavior explicit.
2. **Persistence abstraction:** The agent defined a common `expect`/`actual` `AppDatabase` contract. Android uses `SQLiteOpenHelper`, while iOS uses the system SQLite3 C API directly, avoiding an unnecessary third-party ORM.
3. **Category deletion:** The agent chose `ON DELETE SET NULL`, preserving tasks when their category is deleted while removing the obsolete association.
4. **Navigation data transfer:** The full `Task` object is held in shared Compose state while editing, avoiding duplicated lookup/navigation plumbing for this small application.

## 8. BUILD_LOG Analysis

One significant problem was the initial build failing with `No space left on device` while downloading Android SDK packages. The agent inspected disk usage, removed the failed Kotlin download cache and disposable Gradle/browser caches, and freed approximately 5 GB. The first solution fixed the disk-space condition, allowing the toolchain to continue downloading and building.

A second problem was the Kotlin Toolchain/Amper incremental classpath snapshotter failing at `DirectoryOrJarReader.create` while processing `.aar` dependencies. The agent cleaned the generated `build` directory and retried both Android and shared builds, but that first correction did not solve the toolchain failure. The limitation was recorded instead of being presented as a source-code success.

The build log shows the evolution from the initial template, through the first platform persistence implementation, to the later correction that replaced the iOS `NSUserDefaults` store with SQLite3. That history would be harder to discover from the final code alone because the final files do not show the earlier rejected persistence approach or the environmental build failures.

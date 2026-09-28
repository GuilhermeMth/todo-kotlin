package br.edu.ifpe

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.SQLite3.SQLITE_DONE
import platform.SQLite3.SQLITE_INTEGER
import platform.SQLite3.SQLITE_NULL
import platform.SQLite3.SQLITE_OK
import platform.SQLite3.SQLITE_OPEN_CREATE
import platform.SQLite3.SQLITE_OPEN_READWRITE
import platform.SQLite3.SQLITE_ROW
import platform.SQLite3.sqlite3
import platform.SQLite3.sqlite3_bind_int64
import platform.SQLite3.sqlite3_bind_null
import platform.SQLite3.sqlite3_bind_text
import platform.SQLite3.sqlite3_close
import platform.SQLite3.sqlite3_column_int64
import platform.SQLite3.sqlite3_column_text
import platform.SQLite3.sqlite3_column_type
import platform.SQLite3.sqlite3_exec
import platform.SQLite3.sqlite3_finalize
import platform.SQLite3.sqlite3_last_insert_rowid
import platform.SQLite3.sqlite3_open_v2
import platform.SQLite3.sqlite3_prepare_v2
import platform.SQLite3.sqlite3_stmt

actual fun initializePlatform() = Unit
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

@OptIn(ExperimentalForeignApi::class)
private class IosSqliteDatabase {
    private val directory = "${NSHomeDirectory()}/Library/Application Support"
    private val path = "$directory/todo.sqlite"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        withDatabase { db ->
            exec(db, """
                PRAGMA foreign_keys = ON;
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE
                );
                CREATE TABLE IF NOT EXISTS tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    completed INTEGER NOT NULL DEFAULT 0,
                    dueDateTime TEXT,
                    createdAt INTEGER NOT NULL,
                    categoryId INTEGER REFERENCES categories(id) ON DELETE SET NULL
                );
            """.trimIndent())
        }
    }

    fun tasks(): List<Task> = withDatabase { db ->
        query(db, "SELECT id, title, description, completed, dueDateTime, createdAt, categoryId FROM tasks ORDER BY completed ASC, dueDateTime ASC, id DESC") { statement ->
            Task(
                id = sqlite3_column_int64(statement, 0),
                title = text(statement, 1),
                description = text(statement, 2),
                completed = sqlite3_column_int64(statement, 3) != 0L,
                dueDateTime = nullableText(statement, 4),
                createdAt = sqlite3_column_int64(statement, 5),
                categoryId = nullableLong(statement, 6)
            )
        }
    }

    fun categories(): List<Category> = withDatabase { db ->
        query(db, "SELECT id, name FROM categories ORDER BY name COLLATE NOCASE ASC") { statement ->
            Category(sqlite3_column_int64(statement, 0), text(statement, 1))
        }
    }

    fun saveTask(task: Task): Task = withDatabase { db ->
        val sql = if (task.id == 0L)
            "INSERT INTO tasks(title, description, completed, dueDateTime, createdAt, categoryId) VALUES (?, ?, ?, ?, ?, ?)"
        else
            "UPDATE tasks SET title = ?, description = ?, completed = ?, dueDateTime = ?, createdAt = ?, categoryId = ? WHERE id = ?"
        val id = prepare(db, sql).use { statement ->
            var index = 1
            sqlite3_bind_text(statement, index++, task.title, -1, null)
            sqlite3_bind_text(statement, index++, task.description, -1, null)
            sqlite3_bind_int64(statement, index++, if (task.completed) 1L else 0L)
            bindNullableText(statement, index++, task.dueDateTime)
            sqlite3_bind_int64(statement, index++, task.createdAt)
            bindNullableLong(statement, index++, task.categoryId)
            if (task.id != 0L) sqlite3_bind_int64(statement, index, task.id)
            check(sqlite3_step(statement) == SQLITE_DONE) { "Unable to save task" }
            if (task.id == 0L) sqlite3_last_insert_rowid(db) else task.id
        }
        task.copy(id = id)
    }

    fun deleteTask(id: Long) = withDatabase { db ->
        execute(db, "DELETE FROM tasks WHERE id = ?", id)
    }

    fun saveCategory(category: Category): Category = withDatabase { db ->
        val sql = if (category.id == 0L) "INSERT INTO categories(name) VALUES (?)"
        else "UPDATE categories SET name = ? WHERE id = ?"
        val id = prepare(db, sql).use { statement ->
            sqlite3_bind_text(statement, 1, category.name, -1, null)
            if (category.id != 0L) sqlite3_bind_int64(statement, 2, category.id)
            check(sqlite3_step(statement) == SQLITE_DONE) { "Unable to save category" }
            if (category.id == 0L) sqlite3_last_insert_rowid(db) else category.id
        }
        category.copy(id = id)
    }

    fun deleteCategory(id: Long) = withDatabase { db ->
        execute(db, "DELETE FROM categories WHERE id = ?", id)
    }

    private fun execute(db: CPointer<sqlite3>, sql: String, id: Long) {
        prepare(db, sql).use { statement ->
            sqlite3_bind_int64(statement, 1, id)
            check(sqlite3_step(statement) == SQLITE_DONE) { "Unable to execute database operation" }
        }
    }

    private fun exec(db: CPointer<sqlite3>, sql: String) {
        check(sqlite3_exec(db, sql, null, null, null) == SQLITE_OK) { "Unable to initialize database" }
    }

    private fun <T> query(db: CPointer<sqlite3>, sql: String, mapper: (CPointer<sqlite3_stmt>?) -> T): List<T> {
        return prepare(db, sql).use { statement ->
            buildList {
                while (true) {
                    when (sqlite3_step(statement)) {
                        SQLITE_ROW -> add(mapper(statement))
                        SQLITE_DONE -> break
                        else -> error("Unable to read database")
                    }
                }
            }
        }
    }

    private fun text(statement: CPointer<sqlite3_stmt>?, column: Int) =
        sqlite3_column_text(statement, column)?.toKString() ?: ""

    private fun nullableText(statement: CPointer<sqlite3_stmt>?, column: Int) =
        if (sqlite3_column_type(statement, column) == SQLITE_NULL) null else text(statement, column)

    private fun nullableLong(statement: CPointer<sqlite3_stmt>?, column: Int) =
        if (sqlite3_column_type(statement, column) == SQLITE_NULL) null else sqlite3_column_int64(statement, column)

    private fun bindNullableText(statement: CPointer<sqlite3_stmt>, index: Int, value: String?) {
        if (value == null) sqlite3_bind_null(statement, index)
        else sqlite3_bind_text(statement, index, value, -1, null)
    }

    private fun bindNullableLong(statement: CPointer<sqlite3_stmt>, index: Int, value: Long?) {
        if (value == null) sqlite3_bind_null(statement, index)
        else sqlite3_bind_int64(statement, index, value)
    }

    private fun prepare(db: CPointer<sqlite3>, sql: String): SqliteStatement {
        return memScoped {
            val output = alloc<CPointerVar<sqlite3_stmt>>()
            check(sqlite3_prepare_v2(db, sql, -1, output.ptr, null) == SQLITE_OK) { "Unable to prepare database statement" }
            SqliteStatement(output.value ?: error("SQLite returned an empty statement"))
        }
    }

    private fun <T> withDatabase(block: (CPointer<sqlite3>) -> T): T = memScoped {
        val output = alloc<CPointerVar<sqlite3>>()
        check(sqlite3_open_v2(path, output.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE, null) == SQLITE_OK)
        val db = output.value ?: error("SQLite did not return a database handle")
        try {
            exec(db, "PRAGMA foreign_keys = ON;")
            block(db)
        } finally {
            sqlite3_close(db)
        }
    }

    private class SqliteStatement(private val pointer: CPointer<sqlite3_stmt>) : AutoCloseable {
        override fun close() {
            sqlite3_finalize(pointer)
        }
        fun <T> use(block: (CPointer<sqlite3_stmt>) -> T): T = try {
            block(pointer)
        } finally {
            close()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class AppDatabase actual constructor() {
    private val database by lazy { IosSqliteDatabase() }
    actual fun tasks(): List<Task> = database.tasks()
    actual fun categories(): List<Category> = database.categories()
    actual fun saveTask(task: Task): Task = database.saveTask(task)
    actual fun deleteTask(id: Long) = database.deleteTask(id)
    actual fun saveCategory(category: Category): Category = database.saveCategory(category)
    actual fun deleteCategory(id: Long) = database.deleteCategory(id)
}

actual class NotificationScheduler actual constructor() {
    actual fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = 7u, completionHandler = { _, _ -> }
        )
    }
    actual fun schedule(task: Task) {
        cancel(task.id)
        if (task.completed || task.dueDateTime == null) return
        val formatter = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm" }
        val date = formatter.dateFromString(task.dueDateTime) ?: return
        val seconds = date.timeIntervalSinceNow
        if (seconds <= 0) return
        val content = UNMutableNotificationContent().apply {
            title = "Task reminder"
            body = task.title
            sound = UNNotificationSound.defaultSound
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(task.id.toString(), content, trigger),
            withCompletionHandler = { _ -> }
        )
    }
    actual fun cancel(taskId: Long) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(taskId.toString()))
    }
}

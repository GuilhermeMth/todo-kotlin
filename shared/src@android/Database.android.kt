package br.edu.ifpe

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale

private lateinit var appContext: Context

fun initializePlatform(context: Context) {
    appContext = context.applicationContext
}

actual fun initializePlatform() = Unit
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private class Helper(context: Context) : SQLiteOpenHelper(context, "todo.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', completed INTEGER NOT NULL DEFAULT 0, dueDateTime TEXT, createdAt INTEGER NOT NULL, categoryId INTEGER REFERENCES categories(id) ON DELETE SET NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

actual class AppDatabase actual constructor() {
    private val helper by lazy { Helper(appContext) }

    actual fun tasks(): List<Task> = buildList {
        helper.readableDatabase.query("tasks", null, null, null, null, null, "completed ASC, dueDateTime ASC, id DESC").use {
            while (it.moveToNext()) add(Task(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3) != 0, it.getString(4), it.getLong(5), if (it.isNull(6)) null else it.getLong(6)))
        }
    }
    actual fun categories(): List<Category> = buildList {
        helper.readableDatabase.query("categories", null, null, null, null, null, "name COLLATE NOCASE ASC").use {
            while (it.moveToNext()) add(Category(it.getLong(0), it.getString(1)))
        }
    }
    actual fun saveTask(task: Task): Task {
        val values = android.content.ContentValues().apply {
            put("title", task.title); put("description", task.description); put("completed", if (task.completed) 1 else 0)
            if (task.dueDateTime == null) putNull("dueDateTime") else put("dueDateTime", task.dueDateTime)
            put("createdAt", task.createdAt)
            if (task.categoryId == null) putNull("categoryId") else put("categoryId", task.categoryId)
        }
        val db = helper.writableDatabase
        val id = if (task.id == 0L) db.insertOrThrow("tasks", null, values) else {
            db.update("tasks", values, "id = ?", arrayOf(task.id.toString())); task.id
        }
        return task.copy(id = id)
    }
    actual fun deleteTask(id: Long) { helper.writableDatabase.delete("tasks", "id = ?", arrayOf(id.toString())) }
    actual fun saveCategory(category: Category): Category {
        val db = helper.writableDatabase
        val values = android.content.ContentValues().apply { put("name", category.name) }
        val id = if (category.id == 0L) db.insertOrThrow("categories", null, values) else {
            db.update("categories", values, "id = ?", arrayOf(category.id.toString())); category.id
        }
        return category.copy(id = id)
    }
    actual fun deleteCategory(id: Long) { helper.writableDatabase.delete("categories", "id = ?", arrayOf(id.toString())) }
}

actual class NotificationScheduler actual constructor() {
    private val alarms get() = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    actual fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("tasks", "Task reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    actual fun schedule(task: Task) {
        cancel(task.id)
        val time = task.dueDateTime?.let { runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(it)?.time }.getOrNull() } ?: return
        if (time <= System.currentTimeMillis() || task.completed) return
        val intent = Intent(appContext, ReminderReceiver::class.java).putExtra("taskId", task.id).putExtra("title", task.title)
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, PendingIntent.getBroadcast(appContext, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    actual fun cancel(taskId: Long) {
        alarms.cancel(PendingIntent.getBroadcast(appContext, taskId.hashCode(), Intent(appContext, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
}

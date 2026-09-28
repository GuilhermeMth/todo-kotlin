package br.edu.ifpe

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
    val dueDateTime: String? = null,
    val createdAt: Long = currentTimeMillis(),
    val categoryId: Long? = null
)

data class Category(val id: Long = 0, val name: String)

expect fun currentTimeMillis(): Long
expect fun initializePlatform()

expect class AppDatabase() {
    fun tasks(): List<Task>
    fun categories(): List<Category>
    fun saveTask(task: Task): Task
    fun deleteTask(id: Long)
    fun saveCategory(category: Category): Category
    fun deleteCategory(id: Long)
}

expect class NotificationScheduler() {
    fun requestPermission()
    fun schedule(task: Task)
    fun cancel(taskId: Long)
}

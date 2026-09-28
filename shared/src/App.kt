package br.edu.ifpe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class Screen { Tasks, Editor, Categories }
private enum class StatusFilter { All, Pending, Completed }

@Composable
fun App() {
    MaterialTheme {
        val database = remember { AppDatabase() }
        val notifications = remember { NotificationScheduler() }
        LaunchedEffect(Unit) { notifications.requestPermission() }
        var screen by remember { mutableStateOf(Screen.Tasks) }
        var selectedTask by remember { mutableStateOf<Task?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        fun changed() { refresh++ }
        when (screen) {
            Screen.Tasks -> TaskList(database, notifications, refresh, { selectedTask = it; screen = Screen.Editor }, { selectedTask = null; screen = Screen.Editor }, { screen = Screen.Categories })
            Screen.Editor -> TaskEditor(database, notifications, selectedTask, { screen = Screen.Tasks; changed() }, { screen = Screen.Tasks }, { screen = Screen.Tasks; changed() })
            Screen.Categories -> Categories(database, { screen = Screen.Tasks; changed() })
        }
    }
}

@Composable
private fun TaskList(db: AppDatabase, notifications: NotificationScheduler, refresh: Int, edit: (Task) -> Unit, create: () -> Unit, categories: () -> Unit) {
    var status by remember { mutableStateOf(StatusFilter.All) }
    var category by remember { mutableStateOf<Long?>(null) }
    val tasks = remember(refresh) { db.tasks() }
    val categoryItems = remember(refresh) { db.categories() }
    val visible = tasks.filter { (status == StatusFilter.All || (status == StatusFilter.Completed) == it.completed) && (category == null || it.categoryId == category) }
    Scaffold(topBar = { TopAppBar(title = { Text("My tasks") }, actions = { TextButton(onClick = categories) { Text("Categories") } }) },
        floatingActionButton = { Button(onClick = create) { Text("+ New task") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusFilter.values().forEach { filter -> OutlinedButton(onClick = { status = filter }) { Text(filter.name) } }
            }
            CategoryMenu(categoryItems, category, { category = it })
            if (visible.isEmpty()) Text("No tasks found.", style = MaterialTheme.typography.bodyLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { task ->
                    val categoryName = categoryItems.firstOrNull { it.id == task.categoryId }?.name
                    Card(onClick = { edit(task) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(checked = task.completed, onCheckedChange = {
                                val saved = db.saveTask(task.copy(completed = it))
                                if (it) notifications.cancel(task.id) else notifications.schedule(saved)
                            })
                            Column {
                                Text(task.title, style = MaterialTheme.typography.titleMedium)
                                Text(if (task.completed) "Completed" else "Pending")
                                categoryName?.let { Text("Category: $it") }
                                task.dueDateTime?.let { Text("Due: $it") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMenu(items: List<Category>, selected: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = items.firstOrNull { it.id == selected }?.name ?: "All categories"
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text("All categories") }, { onSelect(null); expanded = false })
            items.forEach { item -> DropdownMenuItem({ Text(item.name) }, { onSelect(item.id); expanded = false }) }
        }
    }
}

@Composable
private fun TaskEditor(db: AppDatabase, notifications: NotificationScheduler, existing: Task?, saveAndBack: () -> Unit, cancel: () -> Unit, deleteAndBack: () -> Unit) {
    var title by remember(existing) { mutableStateOf(existing?.title ?: "") }
    var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
    var due by remember(existing) { mutableStateOf(existing?.dueDateTime ?: "") }
    var completed by remember(existing) { mutableStateOf(existing?.completed ?: false) }
    var categoryId by remember(existing) { mutableStateOf(existing?.categoryId) }
    var error by remember { mutableStateOf<String?>(null) }
    val categories = db.categories()
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "New task" else "Edit task") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(due, { due = it }, label = { Text("Due date/time (yyyy-MM-dd HH:mm)") }, modifier = Modifier.fillMaxWidth())
            CategoryMenu(categories, categoryId, { categoryId = it })
            Row { Checkbox(completed, { completed = it }); Text("Completed", Modifier.padding(top = 12.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (title.isBlank()) error = "Title is required."
                    else if (due.isNotBlank() && !due.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))) error = "Use yyyy-MM-dd HH:mm for the due date."
                    else {
                        val saved = db.saveTask(Task(existing?.id ?: 0, title.trim(), description.trim(), completed, due.ifBlank { null }, existing?.createdAt ?: currentTimeMillis(), categoryId))
                        if (completed || saved.dueDateTime == null) notifications.cancel(saved.id) else notifications.schedule(saved)
                        saveAndBack()
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = cancel) { Text("Cancel") }
                if (existing != null) TextButton(onClick = { db.deleteTask(existing.id); notifications.cancel(existing.id); deleteAndBack() }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun Categories(db: AppDatabase, back: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var dialog by remember { mutableStateOf(false) }
    val categories = remember(refresh) { db.categories() }
    Scaffold(topBar = { TopAppBar(title = { Text("Categories") }, navigationIcon = { TextButton(onClick = back) { Text("Back") } }) },
        floatingActionButton = { Button(onClick = { editing = null; dialog = true }) { Text("+ Category") } }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it.id }) { category ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(category.name, Modifier.weight(1f))
                        TextButton(onClick = { editing = category; dialog = true }) { Text("Rename") }
                        TextButton(onClick = { db.deleteCategory(category.id); refresh++ }) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (dialog) {
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        AlertDialog(onDismissRequest = { dialog = false }, title = { Text(if (editing == null) "New category" else "Rename category") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { db.saveCategory(editing?.copy(name = name.trim()) ?: Category(name = name.trim())); dialog = false; refresh++ } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } })
    }
}

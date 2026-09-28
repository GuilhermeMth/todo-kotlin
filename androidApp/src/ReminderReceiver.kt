package br.edu.ifpe

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(intent.getLongExtra("taskId", 0).hashCode(), NotificationCompat.Builder(context, "tasks")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Task reminder")
            .setContentText(intent.getStringExtra("title") ?: "Task due")
            .setAutoCancel(true).build())
    }
}

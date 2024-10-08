package com.faa.cmsportalcui.AdminAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.faa.cmsportalcui.AdminModel.AdminCompleteTask
import com.faa.cmsportalcui.R

class AdminCompleteTaskAdapter(
    private val taskList: List<AdminCompleteTask>,
    private val staffNames: Map<String, String>
) : RecyclerView.Adapter<AdminCompleteTaskAdapter.TaskViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.complete_task_item, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = taskList[position]
        holder.bind(task, staffNames[task.staffId])
    }

    override fun getItemCount(): Int {
        return taskList.size
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView = itemView.findViewById<TextView>(R.id.title)
        private val userNameTextView = itemView.findViewById<TextView>(R.id.user_name)
        private val timeTextView = itemView.findViewById<TextView>(R.id.time)
        private val roomTextView = itemView.findViewById<TextView>(R.id.room)

        fun bind(task: AdminCompleteTask, staffName: String?) {
            titleTextView.text = task.title
            userNameTextView.text = staffName ?: "Unknown Staff"
            timeTextView.text = "${task.currentDate} ${task.currentTime}"
            roomTextView.text = task.roomNumber
        }
    }

}
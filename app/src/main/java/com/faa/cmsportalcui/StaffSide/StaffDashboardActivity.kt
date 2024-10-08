package com.faa.cmsportalcui.StaffSide

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.faa.cmsportalcui.R
import com.faa.cmsportalcui.StaffAdapter.NotificationAdapter
import com.faa.cmsportalcui.StaffAdapter.TaskAdapter
import com.faa.cmsportalcui.StaffModel.Notification
import com.faa.cmsportalcui.StaffModel.Task
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration


class StaffDashboardActivity : AppCompatActivity() {


    private var completedTaskListener: ListenerRegistration? = null
    private lateinit var profileImage: ImageView
    private lateinit var profileName: TextView
    private lateinit var serrAll: TextView
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var assignedTasksRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var mAuth: FirebaseAuth
    private var staffId: String? = null
    private lateinit var taskAdapter: TaskAdapter
    private var tasksListener: ListenerRegistration? = null
    private val completedTaskIds = mutableSetOf<String>() // To hold IDs of completed tasks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_dashboard)

        profileImage = findViewById(R.id.profileImage)
        profileName = findViewById(R.id.profileName)
        serrAll = findViewById(R.id.seeAllTasks)
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        assignedTasksRecyclerView = findViewById(R.id.assignedTasksRecyclerView)
        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView)

        firestore = FirebaseFirestore.getInstance()
        mAuth = FirebaseAuth.getInstance()

        // Get the staff ID from the intent
        staffId = intent.getStringExtra("staff_id")

        if (staffId != null) {
            // Initialize the TaskAdapter
            taskAdapter = TaskAdapter(emptyList()) { task ->
                val intent = Intent(this, StaffAssignedDetailActivity::class.java).apply {
                    putExtra("id", task.id)
                    putExtra("assignedTaskId", task.assignedTaskId)
                    putExtra("title", task.title)
                    putExtra("description", task.description)
                    putExtra("assignedBy", "Loading...") // Will be updated later
                    putExtra("photoUrl", task.photoUrl)
                    putExtra("status", task.status)
                    putExtra("timestamp", task.timestamp)
                    putExtra("userId", task.userId)
                    putExtra("adminId", task.adminId)
                    putExtra("userType", task.userType)
                    putExtra("staffId", staffId)
                }

                // Determine if the task belongs to an admin or a user and fetch the appropriate details
                if (task.adminId == "lzcmCdafqJ6dg8vAYexS") {
                    // Handle admin tasks
                    fetchRequestDetailsForAdmin(task.adminId, task.id) { location, roomNumber ->
                        intent.putExtra("location", location)
                        intent.putExtra("roomNumber", roomNumber)

                        fetchAssignedByName(null, task.adminId) { assignedBy ->
                            intent.putExtra("assignedBy", assignedBy)
                            startActivity(intent)
                        }
                    }
                } else if (task.userId != null && task.userId.isNotEmpty()) {
                    // Handle user tasks
                    fetchRequestDetailsForUser(task.userId, task.id) { location, roomNumber ->
                        intent.putExtra("location", location)
                        intent.putExtra("roomNumber", roomNumber)

                        fetchAssignedByName(task.userId, null) { assignedBy ->
                            intent.putExtra("assignedBy", assignedBy)
                            startActivity(intent)
                        }
                    }
                } else {
                    startActivity(intent) // Start activity without additional location or roomNumber info
                }
            }


            assignedTasksRecyclerView.layoutManager = LinearLayoutManager(this)
            assignedTasksRecyclerView.adapter = taskAdapter

            // Fetch data
            setupRealTimeStaffUpdates(staffId!!)
            fetchCompletedTaskIds {
                setupRealTimeTaskUpdates(staffId!!)
            }
            fetchNotifications()
        } else {
            Log.e("StaffDashboardActivity", "No staff ID provided")
        }
        serrAll.setOnClickListener{
            val intent = Intent(this@StaffDashboardActivity, StaffAssignedMeActivity::class.java)
            intent.putExtra("staffId", staffId)
            startActivity(intent)
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> true
                R.id.complete -> {
                    val intent = Intent(this@StaffDashboardActivity, StaffCompleteTaskActivity::class.java)
                    intent.putExtra("staffId", staffId)
                    startActivity(intent)
                    true
                }
                R.id.profile -> {
                    val intent = Intent(this@StaffDashboardActivity, StaffProfileDetailActivity::class.java)
                    intent.putExtra("staffId", staffId)
                    startActivity(intent)
                    true
                }
                R.id.equipments -> {
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tasksListener?.remove()
    }

    private fun setupRealTimeStaffUpdates(staffId: String) {
        firestore.collection("staff").document(staffId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("StaffDashboardActivity", "Error fetching staff data", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name")
                    val profileImageUrl = snapshot.getString("profileImageUrl")

                    profileName.text = name ?: "No Name Found"

                    if (profileImageUrl != null && profileImageUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.account)
                            .into(profileImage)
                    } else {
                        profileImage.setImageResource(R.drawable.account)
                    }
                }
            }
    }

    private fun fetchCompletedTaskIds(callback: () -> Unit) {
        // Clear the previous listener if needed (optional step to avoid multiple listeners)
        completedTaskListener?.remove()

        // Set up a real-time listener for the "completeTask" collection
        completedTaskListener = firestore.collection("completeTask")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("StaffDashboardActivity", "Error fetching real-time completed tasks", e)
                    callback() // Proceed with setup even if there's an error
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    completedTaskIds.clear() // Clear the list before updating

                    for (document in snapshots.documents) {
                        completedTaskIds.add(document.id) // Add the completed task IDs
                    }

                    callback() // Proceed with real-time task updates
                } else {
                    Log.d("StaffDashboardActivity", "No completed tasks found")
                    callback() // Proceed even if no tasks are found
                }
            }
    }


    private fun setupRealTimeTaskUpdates(staffId: String) {
        tasksListener = firestore.collection("staff").document(staffId)
            .collection("assignedTasks")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("StaffDashboardActivity", "Error fetching assigned tasks", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val taskList = mutableListOf<Task>()
                    for (document in snapshot.documents) {
                        val task = document.toObject(Task::class.java)
                        if (task != null) {
                            task.assignedTaskId = document.id // Set the assignedTaskId

                            // Add only if the task is not completed
                            if (!completedTaskIds.contains(task.assignedTaskId)) {
                                taskList.add(task)
                            }
                        }
                    }
                    // Update the adapter with the new list of tasks
                    taskAdapter.updateTasks(taskList)
                }
            }
    }


    private fun fetchRequestDetailsForAdmin(adminId: String, taskId: String, callback: (String, String) -> Unit) {
        val requestsRef = firestore.collection("admins")
            .document(adminId)
            .collection("requests")
            .document(taskId)

        requestsRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val location = document.getString("location") ?: "Unknown"
                    val roomNumber = document.getString("roomNumber") ?: "Unknown"
                    callback(location, roomNumber)
                } else {
                    callback("Unknown", "Unknown")
                }
            }
            .addOnFailureListener { e ->
                Log.e("StaffDashboardActivity", "Error fetching request details", e)
                callback("Unknown", "Unknown")
            }
    }

    private fun fetchRequestDetailsForUser(userId: String, taskId: String, callback: (String, String) -> Unit) {
        val requestsRef = firestore.collection("users")
            .document(userId)
            .collection("requests")
            .document(taskId)

        requestsRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val location = document.getString("location") ?: "Unknown"
                    val roomNumber = document.getString("roomNumber") ?: "Unknown"
                    callback(location, roomNumber)
                } else {
                    callback("Unknown", "Unknown")
                }
            }
            .addOnFailureListener { e ->
                Log.e("StaffDashboardActivity", "Error fetching request details for user", e)
                callback("Unknown", "Unknown")
            }
    }

    private fun fetchAssignedByName(userId: String?, adminId: String?, callback: (String) -> Unit) {
        when {
            userId != null && userId.isNotEmpty() -> {
                firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        val userName = document.getString("username") ?: "Unknown"
                        callback(userName)
                    }
                    .addOnFailureListener { e ->
                        Log.e("StaffDashboardActivity", "Error fetching user name", e)
                        callback("Unknown")
                    }
            }
            adminId != null && adminId.isNotEmpty() -> {
                firestore.collection("admins").document(adminId).get()
                    .addOnSuccessListener { document ->
                        val adminName = document.getString("name") ?: "Unknown"
                        callback(adminName)
                    }
                    .addOnFailureListener { e ->
                        Log.e("StaffDashboardActivity", "Error fetching admin name", e)
                        callback("Unknown")
                    }
            }
            else -> {
                callback("Unknown")
            }
        }
    }

    private fun fetchNotifications() {
        firestore.collection("notifications")
            .get()
            .addOnSuccessListener { documents ->
                val notificationList = mutableListOf<Notification>()
                for (document in documents) {
                    val notification = document.toObject(Notification::class.java)
                    notificationList.add(notification)
                }
                val adapter = NotificationAdapter(notificationList)
                notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
                notificationsRecyclerView.adapter = adapter
            }
            .addOnFailureListener { e ->
                Log.e("StaffDashboardActivity", "Error fetching notifications", e)
            }
        fun onBackPressed() {
            // Close all activities in the stack
            finishAffinity()
        }

    }
}

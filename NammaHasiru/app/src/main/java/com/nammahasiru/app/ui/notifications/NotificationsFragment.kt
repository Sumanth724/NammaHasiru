package com.nammahasiru.app.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nammahasiru.app.R
import com.nammahasiru.app.viewmodel.PlantViewModel

class NotificationsFragment : Fragment() {

    private val viewModel: PlantViewModel by activityViewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)
        
        // Handle back button click
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_notifications)
        val emptyView = view.findViewById<TextView>(R.id.tv_empty_notifications)
        val unreadBadge = view.findViewById<TextView>(R.id.tv_unread_badge)

        adapter = NotificationAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewModel.allNotifications.observe(viewLifecycleOwner) { notifications ->
            if (notifications.isNullOrEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                // Fix: Call submitNotifications (with header logic) instead of submitList
                adapter.submitNotifications(notifications)
            }
        }

        viewModel.unreadNotificationsCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = "$count new"
            } else {
                unreadBadge.visibility = View.GONE
            }
        }

        // Logic Change: Mark as read only when user navigates away or manually?
        // Keeping it commented to allow user to see green dots.
        // viewModel.markAllNotificationsRead()
        
        return view
    }
}

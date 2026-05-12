package com.nammahasiru.app.ui.notifications

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nammahasiru.app.R
import com.nammahasiru.app.data.Notification
import java.util.*

class NotificationAdapter : ListAdapter<NotificationAdapter.DataItem, RecyclerView.ViewHolder>(DiffCallback) {

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1

    sealed class DataItem {
        data class NotifItem(val notification: Notification) : DataItem() {
            override val id = notification.id
        }
        data class Header(val title: String) : DataItem() {
            // Use title hash as ID to ensure Today and Earlier headers are treated as different items
            override val id = title.hashCode().toLong()
        }
        abstract val id: Long
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_header_title)
    }

    class NotifViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.tv_notif_title)
        private val messageText: TextView = view.findViewById(R.id.tv_notif_message)
        private val timeText: TextView = view.findViewById(R.id.tv_notif_time)
        private val unreadDot: View = view.findViewById(R.id.view_unread_dot)

        fun bind(notif: Notification) {
            titleText.text = notif.title
            messageText.text = notif.message
            
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                notif.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            timeText.text = relativeTime
            
            unreadDot.setBackgroundResource(
                if (notif.isRead) R.drawable.bg_notif_dot_read 
                else R.drawable.bg_notif_dot_unread
            )
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DataItem.Header -> TYPE_HEADER
            is DataItem.NotifItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification_header, parent, false))
            else -> NotifViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is DataItem.Header) {
            holder.title.text = item.title
        } else if (holder is NotifViewHolder && item is DataItem.NotifItem) {
            holder.bind(item.notification)
        }
    }

    fun submitNotifications(list: List<Notification>?) {
        if (list == null) {
            submitList(null)
            return
        }
        
        val items = mutableListOf<DataItem>()
        val today = Calendar.getInstance()
        
        // Grouping logic
        val todayList = list.filter { isSameDay(it.timestamp, today) }
        val earlierList = list.filter { !isSameDay(it.timestamp, today) }

        if (todayList.isNotEmpty()) {
            items.add(DataItem.Header("Today"))
            items.addAll(todayList.map { DataItem.NotifItem(it) })
        }
        
        if (earlierList.isNotEmpty()) {
            items.add(DataItem.Header("Earlier"))
            items.addAll(earlierList.map { DataItem.NotifItem(it) })
        }
        
        submitList(items)
    }

    private fun isSameDay(timestamp: Long, cal: Calendar): Boolean {
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        return target.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               target.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DataItem>() {
        override fun areItemsTheSame(oldItem: DataItem, newItem: DataItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DataItem, newItem: DataItem): Boolean = oldItem == newItem
    }
}

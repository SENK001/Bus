package com.senk.bus.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.senk.bus.R
import com.senk.bus.data.entity.Schedule

/**
 * 车次列表适配器。
 *
 * 列表顺序由 [com.senk.bus.ui.ScheduleListFragment] 保证「下一班在最前」，NEXT 徽章只挂在
 * adapter 收到的 nextScheduleId 上（而不是固定 position 0），这样定时重排后徽章不会留在已过点的班次上。
 *
 * 使用 DiffUtil 做最小刷新：内容比较里包含「是否为下一班」，因此重排导致徽章易主时会重新绑定
 * 对应 item，而中间项移动不需要重新绑定。
 */
class ScheduleAdapter : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    fun interface OnScheduleClickListener {
        fun onScheduleClick(schedule: Schedule)
    }

    private var schedules: List<Schedule> = emptyList()
    private var nextScheduleId: Long = -1
    private var clickListener: OnScheduleClickListener? = null

    /**
     * @param newSchedules      已按「距下一班由近到远」排好序的车次
     * @param newNextScheduleId 下一班车次的 id，没有有效班次时传 -1
     */
    fun setSchedules(newSchedules: List<Schedule>?, newNextScheduleId: Long) {
        val incoming: List<Schedule> = newSchedules ?: emptyList()
        val previous = schedules
        val previousNextId = nextScheduleId
        val incomingNextId = newNextScheduleId
        if (previous === incoming && previousNextId == incomingNextId) {
            return
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return previous.size
            }

            override fun getNewListSize(): Int {
                return incoming.size
            }

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                return previous[oldPosition].id == incoming[newPosition].id
            }

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val oldItem = previous[oldPosition]
                val newItem = incoming[newPosition]
                val badgeUnchanged = (oldItem.id == previousNextId) ==
                    (newItem.id == incomingNextId)
                return badgeUnchanged && oldItem.departureTime == newItem.departureTime
            }
        })
        schedules = incoming
        nextScheduleId = incomingNextId
        diff.dispatchUpdatesTo(this)
    }

    fun setOnScheduleClickListener(listener: OnScheduleClickListener?) {
        clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.tvDeparture.text = schedule.departureTime
        holder.tvNextBadge.visibility = if (schedule.id == nextScheduleId) {
            View.VISIBLE
        } else {
            View.GONE
        }

        holder.itemView.setOnClickListener {
            clickListener?.onScheduleClick(schedule)
        }
    }

    override fun getItemCount(): Int {
        return schedules.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDeparture: TextView = itemView.findViewById(R.id.tv_departure)
        val tvNextBadge: TextView = itemView.findViewById(R.id.tv_next_badge)
    }
}

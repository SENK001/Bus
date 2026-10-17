package com.senk.bus.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.senk.bus.R
import com.senk.bus.data.entity.Route

class RouteAdapter : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {

    fun interface OnRouteClickListener {
        fun onRouteClick(route: Route)
    }

    fun interface OnRouteLongClickListener {
        fun onRouteLongClick(route: Route, anchor: View)
    }

    private var routes: List<Route> = emptyList()
    private val nextTimes: MutableMap<Long, String> = HashMap()
    private var clickListener: OnRouteClickListener? = null
    private var longClickListener: OnRouteLongClickListener? = null

    /**
     * 替换路线列表，用 DiffUtil 计算最小更新，避免整表刷新导致滚动位置/闪烁。
     * 卡片右侧的下一班时间来自 [nextTimes]，与列表顺序无关。
     */
    fun setRoutes(newRoutes: List<Route>?) {
        val incoming: List<Route> = newRoutes ?: emptyList()
        val previous = routes
        if (previous === incoming) {
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
                return oldItem.name == newItem.name &&
                    oldItem.origin == newItem.origin &&
                    oldItem.destination == newItem.destination &&
                    oldItem.isFavorite == newItem.isFavorite &&
                    oldItem.isDefault == newItem.isDefault
            }
        })
        routes = incoming
        diff.dispatchUpdatesTo(this)
    }

    /** 用一次计算结果替换所有路线的下一班时间；内容没变时不触发刷新。 */
    fun setNextTimes(times: Map<Long, String>?) {
        val incoming: Map<Long, String> = times ?: emptyMap()
        if (nextTimes == incoming) {
            return
        }
        nextTimes.clear()
        nextTimes.putAll(incoming)
        if (routes.isNotEmpty()) {
            notifyItemRangeChanged(0, routes.size)
        }
    }

    fun setOnRouteClickListener(listener: OnRouteClickListener?) {
        clickListener = listener
    }

    fun setOnRouteLongClickListener(listener: OnRouteLongClickListener?) {
        longClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = routes[position]
        holder.tvName.text = route.name
        holder.tvDesc.text = holder.itemView.context.getString(
            R.string.route_desc_format, route.origin, route.destination,
        )

        val nextTime = nextTimes[route.id]
        if (nextTime != null) {
            holder.tvNextTime.text = nextTime
            holder.tvNextTime.visibility = View.VISIBLE
        } else {
            holder.tvNextTime.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            clickListener?.onRouteClick(route)
        }

        holder.itemView.setOnLongClickListener { view ->
            longClickListener?.onRouteLongClick(route, view)
            true
        }
    }

    override fun getItemCount(): Int {
        return routes.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_route_name)
        val tvDesc: TextView = itemView.findViewById(R.id.tv_route_desc)
        val tvNextTime: TextView = itemView.findViewById(R.id.tv_next_time)
    }
}

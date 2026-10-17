package com.senk.bus.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.senk.bus.R
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.NextDeparture
import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule

/**
 * 小组件列表数据源：一次查询 routes 与全部 schedules，在内存中按路线分组后逐项渲染。
 */
class BusWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return BusRemoteViewsFactory(applicationContext)
    }

    private class BusRemoteViewsFactory(private val context: Context) :
        RemoteViewsService.RemoteViewsFactory {

        private var routes: List<Route>? = null
        private var schedulesByRoute: Map<Long, List<Schedule>>? = null
        private var nowHHmm: String? = null

        override fun onCreate() {
            // 首次数据在 onDataSetChanged() 中加载。
        }

        override fun onDataSetChanged() {
            val db = AppDatabase.getInstance(context)
            routes = db.routeDao().getAllRoutesSync()
            schedulesByRoute = groupByRoute(db.scheduleDao().getAllSchedulesSync())
            nowHHmm = NextDeparture.nowHHmm()
        }

        /** 按 routeId 分组；DAO 已按 routeId, departureTime ASC 排序，分组后的列表保持升序。 */
        private fun groupByRoute(schedules: List<Schedule>): Map<Long, List<Schedule>> {
            val grouped = HashMap<Long, MutableList<Schedule>>()
            for (schedule in schedules) {
                // getAllSchedulesSync() 已按 routeId, departureTime ASC 排序，分组后仍保持升序。
                grouped.getOrPut(schedule.routeId) { ArrayList() }.add(schedule)
            }
            return grouped
        }

        override fun onDestroy() {
            routes = null
            schedulesByRoute = null
            nowHHmm = null
        }

        override fun getCount(): Int {
            return routes?.size ?: 0
        }

        override fun getViewAt(position: Int): RemoteViews? {
            val currentRoutes = routes ?: return null
            if (position < 0 || position >= currentRoutes.size) {
                return null
            }
            val route = currentRoutes[position]
            val views = RemoteViews(context.packageName, R.layout.widget_bus_item)
            views.setTextViewText(R.id.item_route_name, route.name)
            views.setTextViewText(
                R.id.item_route_desc,
                route.origin + " → " + route.destination,
            )

            // null 表示该路线没有任何有效班次。
            val next = NextDeparture.find(
                nowHHmm,
                schedulesByRoute?.get(route.id),
            )
            if (next != null) {
                views.setTextViewText(R.id.item_next_time, next.time)
                views.setViewVisibility(R.id.item_next_time, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.item_next_time, View.GONE)
            }

            // fill-in Intent 只携带 routeId；路线名由 MainActivity 查库获取，避免使用陈旧名称。
            val fillInIntent = Intent()
            fillInIntent.putExtra(BusWidgetProvider.EXTRA_ROUTE_ID, route.id)
            views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

            return views
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            val currentRoutes = routes
            if (currentRoutes == null || position < 0 || position >= currentRoutes.size) {
                return position.toLong()
            }
            val route = currentRoutes[position]
            return route.id
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }
}

package com.senk.bus.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.senk.bus.MainActivity
import com.senk.bus.R
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.AppExecutors
import com.senk.bus.data.NextDeparture
import com.senk.bus.data.entity.Schedule

/**
 * 桌面小组件：展示路线列表与每条路线的下一班发车时间。
 *
 * 时效：除 `updatePeriodMillis` 的系统周期刷新外，还会用 [AlarmManager] 在
 * “最早下一班发车时刻”之后触发一次自定义刷新广播，保证发车后小组件能尽快切到下一班。
 * 闹钟使用 [AlarmManager.setAndAllowWhileIdle]（非精确、允许 Doze 下投递），
 * 因此无需申请 `SCHEDULE_EXACT_ALARM` 权限。
 */
class BusWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent != null && ACTION_REFRESH == intent.action) {
            // 闹钟触发：刷新数据，notifyDataChanged 内部会重新安排下一次闹钟。
            notifyDataChanged(context)
        }
    }

    @Suppress("DEPRECATION") // setRemoteAdapter 已废弃，后续可迁移 RemoteCollectionItems
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_bus)

            // 标题点击：打开 App。
            val titleIntent = Intent(context, MainActivity::class.java)
            titleIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val titlePendingIntent = PendingIntent.getActivity(
                context,
                0,
                titleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, titlePendingIntent)

            // 集合型小组件：列表数据由 BusWidgetService 提供。
            val serviceIntent = Intent(context, BusWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 列表项点击模板：Intent 必须是显式组件，且 PendingIntent 必须为 MUTABLE，
            // 否则系统无法把 fill-in Intent 里的 routeId 合并进来，点击会失效。
            val clickIntent = Intent(context, MainActivity::class.java)
            clickIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        scheduleRefresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelRefresh(context)
    }

    companion object {
        private const val TAG = "BusWidgetProvider"

        /** 列表项点击 Intent 中携带的路线 id，路线名由 [MainActivity] 重新查库获取。 */
        const val EXTRA_ROUTE_ID = "widget_route_id"

        /** 自定义刷新广播：到“最早下一班”时刻后由闹钟触发。 */
        const val ACTION_REFRESH = "com.senk.bus.action.WIDGET_REFRESH"

        private const val REFRESH_ALARM_REQUEST_CODE = 100
        private const val MINUTE_MILLIS = 60 * 1000L

        /** 没有任何班次时，30 分钟后兜底刷新一次。 */
        private const val FALLBACK_REFRESH_INTERVAL_MILLIS = 30 * MINUTE_MILLIS

        /** 在下一班发车时刻之后 1 秒刷新，避开分钟边界抖动。 */
        private const val REFRESH_SLACK_MILLIS = 1000L

        /**
         * 通知小组件列表数据已变化，并重新安排下一次刷新闹钟。
         *
         * 可在主线程调用：数据库查询与闹钟计算会切到磁盘线程执行。
         */
        @Suppress("DEPRECATION") // notifyAppWidgetViewDataChanged 已废弃，后续可迁移 RemoteCollectionItems
        @JvmStatic
        fun notifyDataChanged(context: Context?) {
            if (context == null) {
                return
            }
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val provider = ComponentName(appContext, BusWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(provider)
            if (appWidgetIds.isEmpty()) {
                // 桌面没有小组件时无需刷新，也无需安排闹钟。
                return
            }
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
            scheduleRefresh(appContext)
        }

        /** 在磁盘线程计算“最早下一班”的剩余时间，安排一次小组件刷新闹钟。 */
        private fun scheduleRefresh(context: Context) {
            val appContext = context.applicationContext
            AppExecutors.diskIO {
                val delayMillis = computeNextRefreshDelay(appContext)
                scheduleAlarm(appContext, System.currentTimeMillis() + delayMillis)
            }
        }

        /**
         * @return 距下一次应刷新时刻的毫秒数：最早下一班发车时刻 + 1s；
         *         无班次（或查询失败）时返回 30 分钟兜底间隔。
         */
        private fun computeNextRefreshDelay(context: Context): Long {
            return try {
                val schedules = AppDatabase.getInstance(context).scheduleDao().getAllSchedulesSync()
                if (schedules.isEmpty()) {
                    FALLBACK_REFRESH_INTERVAL_MILLIS
                } else {
                    val schedulesByRoute = HashMap<Long, MutableList<Schedule>>()
                    for (schedule in schedules) {
                        // getAllSchedulesSync() 已按 routeId, departureTime ASC 排序，分组后仍保持升序。
                        schedulesByRoute.getOrPut(schedule.routeId) { ArrayList() }.add(schedule)
                    }
                    val nowHHmm = NextDeparture.nowHHmm()
                    var minutesUntilNext = Int.MAX_VALUE
                    for (routeSchedules in schedulesByRoute.values) {
                        val next = NextDeparture.find(nowHHmm, routeSchedules)
                        if (next != null && next.remainingMinutes < minutesUntilNext) {
                            minutesUntilNext = next.remainingMinutes
                        }
                    }
                    when (minutesUntilNext) {
                        Int.MAX_VALUE -> FALLBACK_REFRESH_INTERVAL_MILLIS

                        // 当前分钟内的班次一分钟内就会“过期”；等到分钟边界再刷新，
                        // 避免每个刷新都把闹钟重新排到 1 秒后造成高频触发。
                        0 -> {
                            val millisIntoMinute = System.currentTimeMillis() % MINUTE_MILLIS
                            (MINUTE_MILLIS - millisIntoMinute) + REFRESH_SLACK_MILLIS
                        }

                        else -> minutesUntilNext * MINUTE_MILLIS + REFRESH_SLACK_MILLIS
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to compute next widget refresh delay, use fallback interval", e)
                FALLBACK_REFRESH_INTERVAL_MILLIS
            }
        }

        private fun scheduleAlarm(context: Context, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.w(TAG, "AlarmManager unavailable, widget refresh alarm not scheduled")
                return
            }
            // setAndAllowWhileIdle 是非精确闹钟，无需 SCHEDULE_EXACT_ALARM 权限；
            // 小组件只需在发车后“尽快”刷新，允许系统延后或批量投递。
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                buildRefreshPendingIntent(context),
            )
        }

        private fun cancelRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            alarmManager.cancel(buildRefreshPendingIntent(context))
        }

        private fun buildRefreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, BusWidgetProvider::class.java)
            intent.action = ACTION_REFRESH
            // 闹钟广播只在本应用内闭环，不需要 fill-in，使用 IMMUTABLE 即可。
            return PendingIntent.getBroadcast(
                context,
                REFRESH_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

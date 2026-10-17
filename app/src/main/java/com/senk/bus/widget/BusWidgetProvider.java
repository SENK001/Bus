package com.senk.bus.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.senk.bus.MainActivity;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.NextDeparture;
import com.senk.bus.data.entity.Schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面小组件：展示路线列表与每条路线的下一班发车时间。
 *
 * <p>时效：除 {@code updatePeriodMillis} 的系统周期刷新外，还会用 {@link AlarmManager} 在
 * “最早下一班发车时刻”之后触发一次自定义刷新广播，保证发车后小组件能尽快切到下一班。
 * 闹钟使用 {@link AlarmManager#setAndAllowWhileIdle}（非精确、允许 Doze 下投递），
 * 因此无需申请 {@code SCHEDULE_EXACT_ALARM} 权限。
 */
public class BusWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "BusWidgetProvider";

    /** 列表项点击 Intent 中携带的路线 id，路线名由 {@link MainActivity} 重新查库获取。 */
    public static final String EXTRA_ROUTE_ID = "widget_route_id";

    /** 自定义刷新广播：到“最早下一班”时刻后由闹钟触发。 */
    public static final String ACTION_REFRESH = "com.senk.bus.action.WIDGET_REFRESH";

    private static final int REFRESH_ALARM_REQUEST_CODE = 100;
    private static final long MINUTE_MILLIS = 60 * 1000L;
    /** 没有任何班次时，30 分钟后兜底刷新一次。 */
    private static final long FALLBACK_REFRESH_INTERVAL_MILLIS = 30 * MINUTE_MILLIS;
    /** 在下一班发车时刻之后 1 秒刷新，避开分钟边界抖动。 */
    private static final long REFRESH_SLACK_MILLIS = 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            // 闹钟触发：刷新数据，notifyDataChanged 内部会重新安排下一次闹钟。
            notifyDataChanged(context);
        }
    }

    @Override
    @SuppressWarnings("deprecation") // setRemoteAdapter 已废弃，后续可迁移 RemoteCollectionItems
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_bus);

            // 标题点击：打开 App。
            Intent titleIntent = new Intent(context, MainActivity.class);
            titleIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent titlePendingIntent = PendingIntent.getActivity(
                    context, 0, titleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setOnClickPendingIntent(R.id.widget_title, titlePendingIntent);

            // 集合型小组件：列表数据由 BusWidgetService 提供。
            Intent serviceIntent = new Intent(context, BusWidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setRemoteAdapter(R.id.widget_list, serviceIntent);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty);

            // 列表项点击模板：Intent 必须是显式组件，且 PendingIntent 必须为 MUTABLE，
            // 否则系统无法把 fill-in Intent 里的 routeId 合并进来，点击会失效。
            Intent clickIntent = new Intent(context, MainActivity.class);
            clickIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent clickPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
        scheduleRefresh(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleRefresh(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelRefresh(context);
    }

    /**
     * 通知小组件列表数据已变化，并重新安排下一次刷新闹钟。
     *
     * <p>可在主线程调用：数据库查询与闹钟计算会切到磁盘线程执行。
     */
    @SuppressWarnings("deprecation") // notifyAppWidgetViewDataChanged 已废弃，后续可迁移 RemoteCollectionItems
    public static void notifyDataChanged(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(appContext);
        ComponentName provider = new ComponentName(appContext, BusWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(provider);
        if (appWidgetIds.length == 0) {
            // 桌面没有小组件时无需刷新，也无需安排闹钟。
            return;
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
        scheduleRefresh(appContext);
    }

    /** 在磁盘线程计算“最早下一班”的剩余时间，安排一次小组件刷新闹钟。 */
    private static void scheduleRefresh(Context context) {
        final Context appContext = context.getApplicationContext();
        AppExecutors.diskIO(() -> {
            long delayMillis = computeNextRefreshDelay(appContext);
            scheduleAlarm(appContext, System.currentTimeMillis() + delayMillis);
        });
    }

    /**
     * @return 距下一次应刷新时刻的毫秒数：最早下一班发车时刻 + 1s；
     *         无班次（或查询失败）时返回 30 分钟兜底间隔。
     */
    private static long computeNextRefreshDelay(Context context) {
        try {
            List<Schedule> schedules =
                    AppDatabase.getInstance(context).scheduleDao().getAllSchedulesSync();
            if (schedules == null || schedules.isEmpty()) {
                return FALLBACK_REFRESH_INTERVAL_MILLIS;
            }
            Map<Long, List<Schedule>> schedulesByRoute = new HashMap<>();
            for (Schedule schedule : schedules) {
                if (schedule == null) {
                    continue;
                }
                Long routeId = schedule.getRouteId();
                List<Schedule> routeSchedules = schedulesByRoute.get(routeId);
                if (routeSchedules == null) {
                    routeSchedules = new ArrayList<>();
                    schedulesByRoute.put(routeId, routeSchedules);
                }
                // getAllSchedulesSync() 已按 routeId, departureTime ASC 排序，分组后仍保持升序。
                routeSchedules.add(schedule);
            }
            String nowHHmm = NextDeparture.nowHHmm();
            int minutesUntilNext = Integer.MAX_VALUE;
            for (List<Schedule> routeSchedules : schedulesByRoute.values()) {
                NextDeparture next = NextDeparture.find(nowHHmm, routeSchedules);
                if (next != null && next.remainingMinutes < minutesUntilNext) {
                    minutesUntilNext = next.remainingMinutes;
                }
            }
            if (minutesUntilNext == Integer.MAX_VALUE) {
                return FALLBACK_REFRESH_INTERVAL_MILLIS;
            }
            if (minutesUntilNext == 0) {
                // 当前分钟内的班次一分钟内就会“过期”；等到分钟边界再刷新，
                // 避免每个刷新都把闹钟重新排到 1 秒后造成高频触发。
                long millisIntoMinute = System.currentTimeMillis() % MINUTE_MILLIS;
                return (MINUTE_MILLIS - millisIntoMinute) + REFRESH_SLACK_MILLIS;
            }
            return minutesUntilNext * MINUTE_MILLIS + REFRESH_SLACK_MILLIS;
        } catch (Exception e) {
            Log.w(TAG, "Failed to compute next widget refresh delay, use fallback interval", e);
            return FALLBACK_REFRESH_INTERVAL_MILLIS;
        }
    }

    private static void scheduleAlarm(Context context, long triggerAtMillis) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable, widget refresh alarm not scheduled");
            return;
        }
        // setAndAllowWhileIdle 是非精确闹钟，无需 SCHEDULE_EXACT_ALARM 权限；
        // 小组件只需在发车后“尽快”刷新，允许系统延后或批量投递。
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis,
                buildRefreshPendingIntent(context));
    }

    private static void cancelRefresh(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(buildRefreshPendingIntent(context));
    }

    private static PendingIntent buildRefreshPendingIntent(Context context) {
        Intent intent = new Intent(context, BusWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        // 闹钟广播只在本应用内闭环，不需要 fill-in，使用 IMMUTABLE 即可。
        return PendingIntent.getBroadcast(context, REFRESH_ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

package com.senk.bus.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.NextDeparture;
import com.senk.bus.data.entity.Route;
import com.senk.bus.data.entity.Schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小组件列表数据源：一次查询 routes 与全部 schedules，在内存中按路线分组后逐项渲染。
 */
public class BusWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new BusRemoteViewsFactory(getApplicationContext());
    }

    private static class BusRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

        private final Context context;
        private List<Route> routes;
        private Map<Long, List<Schedule>> schedulesByRoute;
        private String nowHHmm;

        BusRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            // 首次数据在 onDataSetChanged() 中加载。
        }

        @Override
        public void onDataSetChanged() {
            AppDatabase db = AppDatabase.getInstance(context);
            routes = db.routeDao().getAllRoutesSync();
            if (routes == null) {
                routes = new ArrayList<>();
            }
            schedulesByRoute = groupByRoute(db.scheduleDao().getAllSchedulesSync());
            nowHHmm = NextDeparture.nowHHmm();
        }

        /** 按 routeId 分组；DAO 已按 routeId, departureTime ASC 排序，分组后的列表保持升序。 */
        private static Map<Long, List<Schedule>> groupByRoute(List<Schedule> schedules) {
            Map<Long, List<Schedule>> grouped = new HashMap<>();
            if (schedules == null) {
                return grouped;
            }
            for (Schedule schedule : schedules) {
                if (schedule == null) {
                    continue;
                }
                Long routeId = schedule.getRouteId();
                List<Schedule> routeSchedules = grouped.get(routeId);
                if (routeSchedules == null) {
                    routeSchedules = new ArrayList<>();
                    grouped.put(routeId, routeSchedules);
                }
                routeSchedules.add(schedule);
            }
            return grouped;
        }

        @Override
        public void onDestroy() {
            routes = null;
            schedulesByRoute = null;
            nowHHmm = null;
        }

        @Override
        public int getCount() {
            return routes == null ? 0 : routes.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (routes == null || position < 0 || position >= routes.size()) {
                return null;
            }
            Route route = routes.get(position);
            if (route == null) {
                return null;
            }
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_bus_item);
            views.setTextViewText(R.id.item_route_name, route.getName());
            views.setTextViewText(R.id.item_route_desc,
                    route.getOrigin() + " → " + route.getDestination());

            // null 表示该路线没有任何有效班次。
            NextDeparture next = NextDeparture.find(nowHHmm,
                    schedulesByRoute == null ? null : schedulesByRoute.get(route.getId()));
            if (next != null) {
                views.setTextViewText(R.id.item_next_time, next.time);
                views.setViewVisibility(R.id.item_next_time, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.item_next_time, View.GONE);
            }

            // fill-in Intent 只携带 routeId；路线名由 MainActivity 查库获取，避免使用陈旧名称。
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(BusWidgetProvider.EXTRA_ROUTE_ID, route.getId());
            views.setOnClickFillInIntent(R.id.item_root, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            if (routes == null || position < 0 || position >= routes.size()) {
                return position;
            }
            Route route = routes.get(position);
            return route == null ? position : route.getId();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}

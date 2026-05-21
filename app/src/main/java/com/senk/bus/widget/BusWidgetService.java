package com.senk.bus.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.entity.Route;
import com.senk.bus.data.entity.Schedule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BusWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new BusRemoteViewsFactory(getApplicationContext(), intent);
    }

    private static class BusRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

        private final Context context;
        private List<Route> routes = new ArrayList<>();
        private String now;

        BusRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDataSetChanged() {
            AppDatabase db = AppDatabase.getInstance(context);
            routes = db.routeDao().getAllRoutesSync();
            now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        }

        @Override
        public void onDestroy() {
            routes = null;
        }

        @Override
        public int getCount() {
            return routes.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            Route route = routes.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_bus_item);

            views.setTextViewText(R.id.item_route_name, route.getName());
            views.setTextViewText(R.id.item_route_desc,
                    route.getOrigin() + " → " + route.getDestination());

            String nextTime = findNextTime(route.getId());
            if (nextTime != null) {
                views.setTextViewText(R.id.item_next_time, nextTime);
                views.setViewVisibility(R.id.item_next_time, android.view.View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.item_next_time, android.view.View.GONE);
            }

            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(BusWidgetProvider.EXTRA_ROUTE_ID, route.getId());
            fillInIntent.putExtra(BusWidgetProvider.EXTRA_ROUTE_NAME, route.getName());
            views.setOnClickFillInIntent(R.id.item_root, fillInIntent);

            return views;
        }

        private String findNextTime(long routeId) {
            AppDatabase db = AppDatabase.getInstance(context);
            List<Schedule> schedules = db.scheduleDao().getSchedulesForRouteSync(routeId);
            for (Schedule s : schedules) {
                if (s.getDepartureTime().compareTo(now) >= 0) {
                    return s.getDepartureTime();
                }
            }
            if (!schedules.isEmpty()) {
                return schedules.get(0).getDepartureTime();
            }
            return null;
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
            return routes.get(position).getId();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}

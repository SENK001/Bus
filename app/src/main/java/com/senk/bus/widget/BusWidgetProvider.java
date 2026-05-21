package com.senk.bus.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.senk.bus.MainActivity;
import com.senk.bus.R;

public class BusWidgetProvider extends AppWidgetProvider {

    public static final String EXTRA_ROUTE_ID = "widget_route_id";
    public static final String EXTRA_ROUTE_NAME = "widget_route_name";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_bus);

            Intent titleIntent = new Intent(context, MainActivity.class);
            PendingIntent titlePending = PendingIntent.getActivity(
                    context, 0, titleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_title, titlePending);

            Intent serviceIntent = new Intent(context, BusWidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setRemoteAdapter(R.id.widget_list, serviceIntent);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty);

            Intent clickIntent = new Intent(context, MainActivity.class);
            PendingIntent clickPending = PendingIntent.getActivity(
                    context, appWidgetId, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setPendingIntentTemplate(R.id.widget_list, clickPending);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    public static void notifyDataChanged(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, BusWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(provider);
        if (ids.length > 0) {
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
        }
    }
}

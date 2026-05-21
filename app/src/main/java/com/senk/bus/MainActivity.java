package com.senk.bus;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.senk.bus.ui.QueryFragment;
import com.senk.bus.widget.BusWidgetProvider;

public class MainActivity extends AppCompatActivity {

    private QueryFragment queryFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        queryFragment = new QueryFragment();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, queryFragment)
                    .commit();
        } else {
            queryFragment = (QueryFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);
        }

        handleWidgetIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleWidgetIntent(intent);
    }

    private void handleWidgetIntent(Intent intent) {
        if (intent == null) return;
        long routeId = intent.getLongExtra(BusWidgetProvider.EXTRA_ROUTE_ID, -1);
        if (routeId == -1) return;
        String routeName = intent.getStringExtra(BusWidgetProvider.EXTRA_ROUTE_NAME);
        if (routeName == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            if (queryFragment != null) {
                queryFragment.navigateToScheduleList(routeId, routeName);
            }
        });
        intent.removeExtra(BusWidgetProvider.EXTRA_ROUTE_ID);
        intent.removeExtra(BusWidgetProvider.EXTRA_ROUTE_NAME);
    }
}

package com.senk.bus;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Route;
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

        if (savedInstanceState == null) {
            queryFragment = new QueryFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, queryFragment)
                    .commit();
            // 只在首次创建时处理启动 Intent，Activity 重建（savedInstanceState != null）时
            // 不再重放，避免旋转屏幕等场景重复导航。
            handleWidgetIntent(getIntent());
        } else {
            queryFragment = (QueryFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 更新 Activity 持有的 Intent，避免后续 getIntent() 读到旧数据。
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // App 回到前台时刷新桌面小组件（“下一班”的时间口径可能已经变化）。
        BusWidgetProvider.notifyDataChanged(this);
    }

    /**
     * 消费小组件点击带来的数据：校验磁盘上的路线是否仍存在，取数据库中的最新名称后再导航。
     *
     * <p>extra 在读取后立即移除，保证同一 Intent 不会被重复处理。
     */
    private void handleWidgetIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        long routeId = intent.getLongExtra(BusWidgetProvider.EXTRA_ROUTE_ID, -1L);
        // 立即消费 extra，保证同一 Intent 不会被重复处理；路线名统一以数据库为准。
        intent.removeExtra(BusWidgetProvider.EXTRA_ROUTE_ID);
        if (routeId <= 0L) {
            return;
        }

        // 按仓库约定：磁盘线程中只使用主线程捕获的 Application Context 与不可变局部变量。
        final Context appContext = getApplicationContext();
        AppExecutors.diskIO(() -> {
            Route route = AppDatabase.getInstance(appContext).routeDao().getById(routeId);
            runOnUiThread(() -> navigateToScheduleList(route));
        });
    }

    private void navigateToScheduleList(@Nullable Route route) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (route == null) {
            Toast.makeText(getApplicationContext(), R.string.widget_route_missing,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (getSupportFragmentManager().isStateSaved()) {
            // Activity 已保存状态时提交事务不安全（极少见），放弃本次导航而不是崩溃。
            return;
        }
        QueryFragment host = resolveQueryFragment();
        if (host == null) {
            return;
        }
        host.navigateToScheduleList(route.getId(), route.getName());
    }

    /** 拿到可安全执行导航的 QueryFragment；异常状态下重新挂载容器 Fragment 作为兜底。 */
    @Nullable
    private QueryFragment resolveQueryFragment() {
        if (queryFragment == null) {
            queryFragment = (QueryFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);
        }
        if (queryFragment == null) {
            queryFragment = new QueryFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, queryFragment)
                    .commitNow();
        } else if (!queryFragment.isAdded()) {
            // onCreate 里 commit() 的事务可能尚未执行，先落实再导航。
            getSupportFragmentManager().executePendingTransactions();
        }
        return queryFragment.isAdded() ? queryFragment : null;
    }
}

package com.senk.bus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.AppExecutors
import com.senk.bus.data.entity.Route
import com.senk.bus.ui.QueryFragment
import com.senk.bus.widget.BusWidgetProvider

class MainActivity : AppCompatActivity() {

    private var queryFragment: QueryFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            val host = QueryFragment()
            queryFragment = host
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, host)
                .commit()
            // 只在首次创建时处理启动 Intent，Activity 重建（savedInstanceState != null）时
            // 不再重放，避免旋转屏幕等场景重复导航。
            handleWidgetIntent(intent)
        } else {
            queryFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_container) as? QueryFragment
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 更新 Activity 持有的 Intent，避免后续 getIntent() 读到旧数据。
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // App 回到前台时刷新桌面小组件（“下一班”的时间口径可能已经变化）。
        BusWidgetProvider.notifyDataChanged(this)
    }

    /**
     * 消费小组件点击带来的数据：校验磁盘上的路线是否仍存在，取数据库中的最新名称后再导航。
     *
     * extra 在读取后立即移除，保证同一 Intent 不会被重复处理。
     */
    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        val routeId = intent.getLongExtra(BusWidgetProvider.EXTRA_ROUTE_ID, -1L)
        // 立即消费 extra，保证同一 Intent 不会被重复处理；路线名统一以数据库为准。
        intent.removeExtra(BusWidgetProvider.EXTRA_ROUTE_ID)
        if (routeId <= 0L) {
            return
        }

        // 按仓库约定：磁盘线程中只使用主线程捕获的 Application Context 与不可变局部变量。
        val appContext: Context = applicationContext
        AppExecutors.diskIO {
            val route = AppDatabase.getInstance(appContext).routeDao().getById(routeId)
            runOnUiThread { navigateToScheduleList(route) }
        }
    }

    private fun navigateToScheduleList(route: Route?) {
        if (isFinishing || isDestroyed) {
            return
        }
        if (route == null) {
            Toast.makeText(
                applicationContext,
                R.string.widget_route_missing,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (supportFragmentManager.isStateSaved) {
            // Activity 已保存状态时提交事务不安全（极少见），放弃本次导航而不是崩溃。
            return
        }
        val host = resolveQueryFragment() ?: return
        host.navigateToScheduleList(route.id, route.name)
    }

    /** 拿到可安全执行导航的 QueryFragment；异常状态下重新挂载容器 Fragment 作为兜底。 */
    private fun resolveQueryFragment(): QueryFragment? {
        var host = queryFragment
        if (host == null) {
            host = supportFragmentManager
                .findFragmentById(R.id.fragment_container) as? QueryFragment
        }
        if (host == null) {
            val created = QueryFragment()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, created)
                .commitNow()
            queryFragment = created
            return if (created.isAdded) created else null
        }
        if (!host.isAdded) {
            // onCreate 里 commit() 的事务可能尚未执行，先落实再导航。
            supportFragmentManager.executePendingTransactions()
        }
        queryFragment = host
        return if (host.isAdded) host else null
    }
}

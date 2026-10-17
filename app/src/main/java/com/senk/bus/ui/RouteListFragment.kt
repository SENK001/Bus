package com.senk.bus.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.senk.bus.R
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.AppExecutors
import com.senk.bus.data.NextDeparture
import com.senk.bus.data.entity.Route
import com.senk.bus.data.entity.Schedule
import com.senk.bus.ui.adapter.RouteAdapter
import com.senk.bus.widget.BusWidgetProvider
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class RouteListFragment : Fragment() {

    private var adapter: RouteAdapter? = null
    private var handler: Handler? = null
    private var minuteTicker: Runnable? = null

    /** 最近一次 LiveData 下发的路线列表（下发给磁盘线程重算下一班用）。 */
    private var routes: List<Route> = ArrayList()

    private var importing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_route_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val recyclerView = view.findViewById<RecyclerView>(R.id.route_recycler_view)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)

        toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_add -> {
                        queryParent()?.navigateToAddRoute()
                        true
                    }

                    R.id.action_export -> {
                        exportData()
                        true
                    }

                    R.id.action_import -> {
                        importData()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val routeAdapter = RouteAdapter()
        adapter = routeAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = routeAdapter

        routeAdapter.setOnRouteClickListener { route ->
            queryParent()?.navigateToScheduleList(route.id, route.name)
        }

        routeAdapter.setOnRouteLongClickListener { route, anchor ->
            showPopupMenu(route, anchor)
        }

        AppDatabase.getInstance(requireContext()).routeDao().getAllRoutes()
            .observe(viewLifecycleOwner) { list ->
                routes = list
                routeAdapter.setRoutes(routes)
                tvEmpty.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (routes.isEmpty()) View.GONE else View.VISIBLE
                loadNextTimes()
            }
    }

    override fun onResume() {
        super.onResume()
        startMinuteTicker()
    }

    override fun onPause() {
        super.onPause()
        stopMinuteTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMinuteTicker()
        handler?.removeCallbacksAndMessages(null)
    }

    private fun startMinuteTicker() {
        stopMinuteTicker()
        minuteTicker = Runnable {
            if (isAdded && view != null) {
                loadNextTimes()
                scheduleNextMinuteTick()
            }
        }
        if (routes.isNotEmpty()) {
            // 从其它页面返回时立即刷新，避免旧数据停留到下一个整分钟。
            loadNextTimes()
        }
        scheduleNextMinuteTick()
    }

    /** 对齐到下一个整分钟触发，保证下一班时间最多滞后一次刷新周期。 */
    private fun scheduleNextMinuteTick() {
        val currentHandler = handler ?: return
        val ticker = minuteTicker ?: return
        val now = System.currentTimeMillis()
        val delay = MINUTE_REFRESH_INTERVAL - (now % MINUTE_REFRESH_INTERVAL)
        currentHandler.postDelayed(ticker, delay)
    }

    private fun stopMinuteTicker() {
        val currentHandler = handler
        val ticker = minuteTicker
        if (currentHandler != null && ticker != null) {
            currentHandler.removeCallbacks(ticker)
        }
    }

    /**
     * 磁盘线程一次查出全部车次，按路线分组后用 [NextDeparture] 统一计算下一班，
     * 再回主线程批量更新 adapter，避免每路线一次回调、也避免后台线程碰 Fragment API。
     */
    private fun loadNextTimes() {
        val currentAdapter = adapter ?: return
        val appContext = requireContext().applicationContext
        val hostActivity = requireActivity()
        val currentRoutes = ArrayList(routes)
        if (currentRoutes.isEmpty()) {
            currentAdapter.setNextTimes(emptyMap<Long, String>())
            return
        }
        AppExecutors.diskIO {
            val nextTimes = HashMap<Long, String>()
            var success = true
            try {
                val now = NextDeparture.nowHHmm()
                val allSchedules = AppDatabase.getInstance(appContext)
                    .scheduleDao().getAllSchedulesSync()
                val schedulesByRoute = HashMap<Long, MutableList<Schedule>>()
                for (schedule in allSchedules) {
                    val routeSchedules = schedulesByRoute.getOrPut(schedule.routeId) {
                        ArrayList()
                    }
                    routeSchedules.add(schedule)
                }
                for (route in currentRoutes) {
                    val next = NextDeparture.find(now, schedulesByRoute[route.id])
                    if (next != null) {
                        nextTimes[route.id] = next.time
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load next departure times", e)
                success = false
            }
            if (success) {
                val result = nextTimes
                hostActivity.runOnUiThread {
                    if (isAdded) {
                        adapter?.setNextTimes(result)
                    }
                }
            }
        }
    }

    private fun showPopupMenu(route: Route, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, if (route.isFavorite) R.string.unfavorite else R.string.favorite)
        popup.menu.add(0, 2, 1, if (route.isDefault) R.string.unset_default else R.string.set_default)
        popup.menu.add(0, 3, 2, R.string.edit)
        popup.menu.add(0, 4, 3, R.string.delete)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    toggleFavorite(route)
                    true
                }

                2 -> {
                    toggleDefault(route)
                    true
                }

                3 -> {
                    queryParent()?.navigateToEditRoute(route.id)
                    true
                }

                4 -> {
                    deleteRoute(route)
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    /** 收藏/取消收藏：写库成功后才提示，失败回主线程提示错误。 */
    private fun toggleFavorite(route: Route) {
        val appContext = requireContext().applicationContext
        val hostActivity = requireActivity()
        val routeId = route.id
        val favorite = !route.isFavorite
        val successMessage = getString(
            if (favorite) R.string.favorite_added else R.string.favorite_removed,
        )
        val failureMessage = getString(R.string.operation_failed)
        AppExecutors.diskIO {
            var success = true
            try {
                AppDatabase.getInstance(appContext).routeDao().setFavorite(routeId, favorite)
                BusWidgetProvider.notifyDataChanged(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite for route $routeId", e)
                success = false
            }
            val updated = success
            hostActivity.runOnUiThread {
                if (isAdded) {
                    Toast.makeText(
                        appContext,
                        if (updated) successMessage else failureMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /** 设为/取消默认：写库成功后才提示，失败提示并保留原状态。 */
    private fun toggleDefault(route: Route) {
        val appContext = requireContext().applicationContext
        val hostActivity = requireActivity()
        val routeId = route.id
        val wasDefault = route.isDefault
        val successMessage = getString(
            if (wasDefault) R.string.default_unset else R.string.default_set,
        )
        val failureMessage = getString(R.string.operation_failed)
        AppExecutors.diskIO {
            var success = true
            try {
                val db = AppDatabase.getInstance(appContext)
                if (wasDefault) {
                    db.routeDao().clearAllDefaults()
                } else {
                    db.routeDao().setDefault(routeId)
                }
                BusWidgetProvider.notifyDataChanged(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle default for route $routeId", e)
                success = false
            }
            val updated = success
            hostActivity.runOnUiThread {
                if (isAdded) {
                    Toast.makeText(
                        appContext,
                        if (updated) successMessage else failureMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun deleteRoute(route: Route) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.confirm_delete_route, route.name))
            .setPositiveButton(R.string.delete) { _, _ -> performDeleteRoute(route) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 删除成功才提示成功；车次由外键 CASCADE 级联删除。 */
    private fun performDeleteRoute(route: Route) {
        val appContext = requireContext().applicationContext
        val hostActivity = requireActivity()
        val routeId = route.id
        val successMessage = getString(R.string.route_deleted, route.name)
        val failureMessage = getString(R.string.delete_failed)
        AppExecutors.diskIO {
            var success = true
            try {
                val db = AppDatabase.getInstance(appContext)
                val entity = db.routeDao().getById(routeId)
                if (entity != null) {
                    db.routeDao().delete(entity)
                }
                BusWidgetProvider.notifyDataChanged(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete route $routeId", e)
                success = false
            }
            val deleted = success
            hostActivity.runOnUiThread {
                if (isAdded) {
                    Toast.makeText(
                        appContext,
                        if (deleted) successMessage else failureMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 导出 / 导入
    // ---------------------------------------------------------------------

    /**
     * 导出为 Base64(JSON)：{"version":2,"routes":[{name,origin,destination,isFavorite,
     * isDefault,schedules:[{departureTime}]}]}，复制到剪贴板。
     */
    private fun exportData() {
        val appContext = requireContext().applicationContext
        val hostActivity = requireActivity()
        val successMessage = getString(R.string.export_success)
        val failureMessage = getString(R.string.export_error)
        AppExecutors.diskIO {
            var payload: String? = null
            try {
                payload = buildExportPayload(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build export payload", e)
            }
            val base64 = payload
            hostActivity.runOnUiThread {
                if (isAdded) {
                    if (base64 == null) {
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        try {
                            val clipboard = appContext.getSystemService(
                                Context.CLIPBOARD_SERVICE,
                            ) as? ClipboardManager
                                ?: throw IllegalStateException("ClipboardManager unavailable")
                            clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, base64))
                            Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy export data to clipboard", e)
                            Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun buildExportPayload(appContext: Context): String {
        val db = AppDatabase.getInstance(appContext)
        val allRoutes = db.routeDao().getAllRoutesSync()
        val allSchedules = db.scheduleDao().getAllSchedulesSync()
        val schedulesByRoute = HashMap<Long, MutableList<Schedule>>()
        for (schedule in allSchedules) {
            val routeSchedules = schedulesByRoute.getOrPut(schedule.routeId) {
                ArrayList()
            }
            routeSchedules.add(schedule)
        }

        val routesArray = JSONArray()
        for (route in allRoutes) {
            val routeObject = JSONObject()
            routeObject.put("name", route.name)
            routeObject.put("origin", route.origin)
            routeObject.put("destination", route.destination)
            routeObject.put("isFavorite", route.isFavorite)
            routeObject.put("isDefault", route.isDefault)

            val schedulesArray = JSONArray()
            val routeSchedules = schedulesByRoute[route.id]
            if (routeSchedules != null) {
                for (schedule in routeSchedules) {
                    val scheduleObject = JSONObject()
                    scheduleObject.put("departureTime", schedule.departureTime)
                    schedulesArray.put(scheduleObject)
                }
            }
            routeObject.put("schedules", schedulesArray)
            routesArray.put(routeObject)
        }

        val data = JSONObject()
        data.put("version", EXPORT_VERSION)
        data.put("routes", routesArray)
        return Base64.encodeToString(
            data.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP,
        )
    }

    /**
     * 从剪贴板导入：Base64 解码容错、字段与时间严格校验、整段导入在单个 Room 事务内完成，
     * 并按 name+origin+destination 去重合并，重复导入不会产生整份重复数据。
     */
    private fun importData() {
        if (importing) {
            return
        }
        val failureMessage = getString(R.string.import_error)
        var clipboardText: String? = null
        try {
            val clipboard = requireContext().getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as? ClipboardManager
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                showToast(failureMessage)
                return
            }
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                showToast(failureMessage)
                return
            }
            val text = clip.getItemAt(0).text
            if (text == null) {
                showToast(failureMessage)
                return
            }
            clipboardText = text.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read clipboard for import", e)
            showToast(failureMessage)
            return
        }
        // try/catch 中异常路径已提前返回，此处 clipboardText 必为已赋值的非空文本。
        if (clipboardText.isEmpty()) {
            showToast(failureMessage)
            return
        }

        importing = true
        val base64 = clipboardText
        val appContext = requireContext().applicationContext
        val hostActivity = requireActivity()
        AppExecutors.diskIO {
            var stats: IntArray? = null
            try {
                val decoded = Base64.decode(base64, Base64.DEFAULT)
                val json = String(decoded, StandardCharsets.UTF_8)
                val data = JSONObject(json)
                val routesArray = data.optJSONArray("routes")
                    ?: throw JSONException("Import payload has no routes array")
                stats = importIntoDatabase(AppDatabase.getInstance(appContext), routesArray)
                try {
                    BusWidgetProvider.notifyDataChanged(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh widget after import", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
            }
            val result = stats
            hostActivity.runOnUiThread {
                importing = false
                if (isAdded) {
                    if (result == null) {
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.import_result, result[0], result[1]),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * 在单个事务内写入导入数据。
     *
     * @return [0] = 成功处理的路线数, [1] = 跳过的无效数据条数
     */
    private fun importIntoDatabase(db: AppDatabase, routesArray: JSONArray): IntArray {
        val stats = intArrayOf(0, 0)
        db.runInTransaction {
            // isDefault 归一化：先清空现有默认，再让导入项中第一条 isDefault=true 生效。
            db.routeDao().clearAllDefaults()
            val knownRoutes = ArrayList(db.routeDao().getAllRoutesSync())
            var defaultAssigned = false

            for (i in 0 until routesArray.length()) {
                val routeObject = routesArray.optJSONObject(i)
                if (routeObject == null) {
                    stats[1] += 1
                    continue
                }
                val name = readText(routeObject, "name")
                val origin = readText(routeObject, "origin")
                val destination = readText(routeObject, "destination")
                if (name.isEmpty() || origin.isEmpty() || destination.isEmpty()) {
                    stats[1] += 1
                    continue
                }

                val validTimes = ArrayList<String>()
                val schedulesArray = routeObject.optJSONArray("schedules")
                if (schedulesArray != null) {
                    for (j in 0 until schedulesArray.length()) {
                        val departureTime = readDepartureTime(schedulesArray.optJSONObject(j))
                        if (departureTime != null) {
                            validTimes.add(departureTime)
                        } else {
                            stats[1] += 1
                        }
                    }
                }

                val favorite = routeObject.optBoolean("isFavorite", false)
                val wantsDefault = routeObject.optBoolean("isDefault", false) && !defaultAssigned

                val existing = findExistingRoute(knownRoutes, name, origin, destination)
                val route: Route
                if (existing == null) {
                    route = Route()
                    route.name = name
                    route.origin = origin
                    route.destination = destination
                    route.isFavorite = favorite
                    route.isDefault = wantsDefault
                    route.id = db.routeDao().insert(route)
                    knownRoutes.add(route)
                } else {
                    route = existing
                    var changed = false
                    if (favorite && !route.isFavorite) {
                        route.isFavorite = true
                        changed = true
                    }
                    if (wantsDefault && !route.isDefault) {
                        route.isDefault = true
                        changed = true
                    }
                    if (changed) {
                        db.routeDao().update(route)
                    }
                }
                if (wantsDefault) {
                    defaultAssigned = true
                }

                val existingTimes = HashSet<String>()
                for (existingSchedule in db.scheduleDao().getSchedulesForRouteSync(route.id)) {
                    existingTimes.add(existingSchedule.departureTime)
                }
                for (departureTime in validTimes) {
                    if (!existingTimes.add(departureTime)) {
                        // 同一导入数据内重复、或数据库已存在该时间：跳过，保证幂等。
                        continue
                    }
                    val schedule = Schedule()
                    schedule.routeId = route.id
                    schedule.departureTime = departureTime
                    db.scheduleDao().insert(schedule)
                }
                stats[0] += 1
            }
        }
        return stats
    }

    private fun findExistingRoute(
        routes: List<Route>,
        name: String,
        origin: String,
        destination: String,
    ): Route? {
        for (route in routes) {
            if (name == route.name && origin == route.origin && destination == route.destination) {
                return route
            }
        }
        return null
    }

    private fun readText(obj: JSONObject?, key: String): String {
        if (obj == null) {
            return ""
        }
        val value = obj.opt(key)
        return if (value is String) value.trim() else ""
    }

    private fun readDepartureTime(obj: JSONObject?): String? {
        val departureTime = readText(obj, "departureTime")
        return if (NextDeparture.isValid(departureTime)) departureTime else null
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun queryParent(): QueryFragment? {
        val parent = parentFragment
        return parent as? QueryFragment
    }

    companion object {
        private const val TAG = "RouteListFragment"

        /** 路线卡片「下一班」的自动刷新周期：每分钟按当前时间重算一次。 */
        private const val MINUTE_REFRESH_INTERVAL = 60_000L
        private const val CLIP_LABEL = "bus_data"

        /** 导出 JSON 结构版本；格式变化时必须递增并在导入侧兼容旧版本。 */
        private const val EXPORT_VERSION = 2
    }
}

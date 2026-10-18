package com.senk.bus.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.itheima.wheelpicker.WheelPicker
import com.senk.bus.R
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.AppExecutors
import com.senk.bus.data.NextDeparture
import com.senk.bus.data.entity.Schedule
import com.senk.bus.ui.adapter.ScheduleAdapter
import com.senk.bus.widget.BusWidgetProvider
import kotlin.math.min

class ScheduleListFragment : Fragment() {

    private var routeId: Long = 0L
    private var adapter: ScheduleAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var banner: TextView? = null
    private var emptyView: TextView? = null
    private var handler: Handler? = null
    private var refreshRunnable: Runnable? = null

    /** 数据库中的车次（departureTime 升序），定时刷新时重新排序后使用。 */
    private var schedules: List<Schedule> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            routeId = args.getLong(ARG_ROUTE_ID)
        }
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val recyclerView = view.findViewById<RecyclerView>(R.id.schedule_recycler_view)
        this.recyclerView = recyclerView
        banner = view.findViewById<TextView>(R.id.next_departure_banner)
        emptyView = view.findViewById<TextView>(R.id.tv_empty_schedules)

        toolbar.setNavigationOnClickListener { navigateBack() }

        toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_schedule_list, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_add_schedule) {
                    val parent = queryParent()
                    if (parent != null) {
                        parent.navigateToAddSchedule(routeId)
                    }
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val adapter = ScheduleAdapter()
        this.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        adapter.setOnScheduleClickListener { schedule -> showEditDialog(schedule) }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateBannerStyle(recyclerView.computeVerticalScrollOffset())
            }
        })

        AppDatabase.getInstance(requireContext()).scheduleDao()
            .getSchedulesForRoute(routeId)
            .observe(viewLifecycleOwner) { list ->
                schedules = list ?: emptyList()
                renderSchedules()
            }

        val args = arguments
        if (args != null) {
            toolbar.title = args.getString(ARG_ROUTE_NAME)
        }
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
    }

    /**
     * 按「距下一班由近到远」重新排序后刷新列表与横幅，保证 NEXT 徽章始终是下一条车次。
     * 统一使用 [NextDeparture]，不再各自实现 diffMinutes。
     */
    private fun renderSchedules() {
        if (!isAdded() || getView() == null) {
            return
        }
        val adapter = adapter ?: return
        val banner = banner ?: return
        val recyclerView = recyclerView ?: return
        val emptyView = emptyView ?: return
        val now = NextDeparture.nowHHmm()
        val sorted = sortByProximity(schedules, now)
        val next = NextDeparture.find(now, sorted)
        adapter.setSchedules(sorted, next?.schedule?.id ?: -1L)
        updateBanner(next)
        val empty = sorted.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        val refreshRunnable = Runnable {
            renderSchedules()
            scheduleNextRefresh()
        }
        this.refreshRunnable = refreshRunnable
        renderSchedules()
        scheduleNextRefresh()
    }

    /** 对齐到下一个整分钟刷新，避免倒计时/下一班最多滞后一整分钟。 */
    private fun scheduleNextRefresh() {
        val handler = handler ?: return
        val refreshRunnable = refreshRunnable ?: return
        val now = System.currentTimeMillis()
        val delay = REFRESH_INTERVAL - (now % REFRESH_INTERVAL)
        handler.postDelayed(refreshRunnable, delay)
    }

    private fun stopAutoRefresh() {
        val handler = handler ?: return
        val refreshRunnable = refreshRunnable ?: return
        handler.removeCallbacks(refreshRunnable)
    }

    private fun showEditDialog(schedule: Schedule) {
        if (!isAdded()) {
            return
        }
        val scheduleId = schedule.id
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_schedule, null)

        val timePicker = WheelTimePicker(
            dialogView.findViewById<WheelPicker>(R.id.wheel_hour),
            dialogView.findViewById<WheelPicker>(R.id.wheel_minute),
            requireContext(),
        )

        var minutesOfDay = NextDeparture.parseMinutes(schedule.departureTime)
        if (minutesOfDay < 0) {
            minutesOfDay = NextDeparture.parseMinutes(NextDeparture.nowHHmm())
        }
        if (minutesOfDay >= 0) {
            timePicker.setTime(minutesOfDay / 60, minutesOfDay % 60)
        }

        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tv_subtitle)
        tvSubtitle.text = departInText(
            NextDeparture.format(timePicker.getHour() * 60 + timePicker.getMinute()),
        )

        timePicker.setOnTimeChangedListener { hour, minute ->
            tvSubtitle.text = departInText(NextDeparture.format(hour * 60 + minute))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        val window = dialog.window
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val saveButton = dialogView.findViewById<View>(R.id.btn_save)
        val saveInFlight = booleanArrayOf(false)

        dialogView.findViewById<View>(R.id.btn_delete).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.confirm_delete_schedule)
                .setPositiveButton(R.string.delete) { _, _ -> deleteSchedule(scheduleId, dialog) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        saveButton.setOnClickListener {
            if (saveInFlight[0]) {
                return@setOnClickListener
            }
            saveInFlight[0] = true
            saveButton.isEnabled = false
            saveSchedule(
                scheduleId,
                NextDeparture.format(timePicker.getHour() * 60 + timePicker.getMinute()),
                dialog,
                saveButton,
                saveInFlight,
            )
        }

        dialog.show()
    }

    private fun saveSchedule(
        scheduleId: Long,
        departureTime: String,
        dialog: AlertDialog,
        saveButton: View,
        saveInFlight: BooleanArray,
    ) {
        saveInFlight[0] = true
        val appContext: Context = requireContext().applicationContext
        val hostActivity: Activity = requireActivity()
        val successMessage = getString(R.string.schedule_updated)
        val failureMessage = getString(R.string.save_failed)

        AppExecutors.diskIO {
            var success = true
            try {
                val db = AppDatabase.getInstance(appContext)
                val entity = db.scheduleDao().getById(scheduleId)
                if (entity == null) {
                    success = false
                } else {
                    entity.departureTime = departureTime
                    db.scheduleDao().update(entity)
                    BusWidgetProvider.notifyDataChanged(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update schedule $scheduleId", e)
                success = false
            }
            val updated = success
            hostActivity.runOnUiThread {
                if (updated) {
                    if (isAdded()) {
                        Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show()
                    }
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                } else {
                    saveInFlight[0] = false
                    saveButton.isEnabled = true
                    if (isAdded()) {
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun deleteSchedule(scheduleId: Long, dialog: AlertDialog) {
        val appContext: Context = requireContext().applicationContext
        val hostActivity: Activity = requireActivity()
        val successMessage = getString(R.string.schedule_deleted)
        val failureMessage = getString(R.string.delete_failed)

        AppExecutors.diskIO {
            var success = true
            try {
                val db = AppDatabase.getInstance(appContext)
                val entity = db.scheduleDao().getById(scheduleId)
                if (entity != null) {
                    db.scheduleDao().delete(entity)
                }
                BusWidgetProvider.notifyDataChanged(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete schedule $scheduleId", e)
                success = false
            }
            val deleted = success
            hostActivity.runOnUiThread {
                if (!isAdded()) {
                    return@runOnUiThread
                }
                if (deleted) {
                    Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show()
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                } else {
                    Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateBanner(next: NextDeparture?) {
        val banner = banner ?: return
        if (next == null) {
            banner.visibility = View.GONE
            return
        }
        val remaining = remainingText(next.remainingMinutes)
        if (next.remainingMinutes == 0) {
            banner.text = remaining
        } else {
            banner.text = getString(R.string.next_departure_format, remaining)
        }
        banner.visibility = View.VISIBLE
    }

    private fun remainingText(remainingMinutes: Int): String {
        if (remainingMinutes <= 0) {
            return getString(R.string.depart_now)
        }
        val hours = remainingMinutes / 60
        val minutes = remainingMinutes % 60
        return if (hours > 0) {
            getString(R.string.time_remaining_hm, hours, minutes)
        } else {
            getString(R.string.time_remaining_m, minutes)
        }
    }

    /** 编辑弹窗副标题：「N 小时后发车」；时间非法时返回空串。 */
    private fun departInText(departureTime: String): String {
        val remaining = NextDeparture.minutesUntil(NextDeparture.nowHHmm(), departureTime)
        if (remaining < 0) {
            return ""
        }
        if (remaining == 0) {
            return getString(R.string.depart_now)
        }
        val hours = remaining / 60
        val minutes = remaining % 60
        return if (hours > 0) {
            getString(R.string.depart_in_hm, hours, minutes)
        } else {
            getString(R.string.depart_in_m, minutes)
        }
    }

    private fun updateBannerStyle(scrollY: Int) {
        val banner = banner ?: return
        val density = resources.displayMetrics.density
        val maxPadding = MAX_PADDING_DP * density
        val minPadding = MIN_PADDING_DP * density
        val maxScroll = (100 * density).toInt()

        val fraction = min(1f, scrollY / maxScroll.toFloat())
        val padding = (maxPadding - fraction * (maxPadding - minPadding)).toInt()
        val lp = banner.layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = padding
        lp.bottomMargin = padding
        banner.layoutParams = lp

        if (fraction >= 1f) {
            val extraScroll = scrollY - maxScroll
            val fontFraction = min(1f, extraScroll / (100 * density))
            banner.textSize = MAX_TEXT_SP - fontFraction * (MAX_TEXT_SP - MIN_TEXT_SP)
        } else {
            banner.textSize = MAX_TEXT_SP
        }
    }

    /**
     * 按「从现在开始的剩余分钟数」升序排序；非法时间排到最后。
     * 输入为 DAO 的 departureTime 升序列表，排序后第一条即下一班。
     */
    private fun sortByProximity(source: List<Schedule>, now: String): List<Schedule> {
        val sorted = ArrayList(source)
        sorted.sortWith { first, second ->
            val firstRemaining = NextDeparture.minutesUntil(now, first.departureTime)
            val secondRemaining = NextDeparture.minutesUntil(now, second.departureTime)
            if (firstRemaining < 0 || secondRemaining < 0) {
                if (firstRemaining < 0 && secondRemaining < 0) {
                    return@sortWith 0
                }
                return@sortWith if (firstRemaining < 0) 1 else -1
            }
            if (firstRemaining != secondRemaining) {
                return@sortWith firstRemaining.compareTo(secondRemaining)
            }
            first.departureTime.compareTo(second.departureTime)
        }
        return sorted
    }

    private fun queryParent(): QueryFragment? {
        val parent = parentFragment
        return parent as? QueryFragment
    }

    private fun navigateBack() {
        val manager: FragmentManager = parentFragmentManager
        if (manager.isStateSaved) {
            return
        }
        if (manager.backStackEntryCount > 0) {
            manager.popBackStackImmediate()
        }
    }

    companion object {
        private const val TAG = "ScheduleListFragment"
        private const val ARG_ROUTE_ID = "route_id"
        private const val ARG_ROUTE_NAME = "route_name"

        /** 车次列表/横幅的自动刷新周期：每分钟重算一次「下一班」。 */
        private const val REFRESH_INTERVAL = 60_000L
        private const val MAX_PADDING_DP = 50
        private const val MIN_PADDING_DP = 5
        private const val MAX_TEXT_SP = 20f
        private const val MIN_TEXT_SP = 14f

        @JvmStatic
        fun newInstance(routeId: Long, routeName: String?): ScheduleListFragment {
            val fragment = ScheduleListFragment()
            val args = Bundle()
            args.putLong(ARG_ROUTE_ID, routeId)
            args.putString(ARG_ROUTE_NAME, routeName)
            fragment.arguments = args
            return fragment
        }
    }
}

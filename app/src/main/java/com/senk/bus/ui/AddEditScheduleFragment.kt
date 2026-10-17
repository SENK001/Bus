package com.senk.bus.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.MaterialToolbar
import com.itheima.wheelpicker.WheelPicker
import com.senk.bus.R
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.AppExecutors
import com.senk.bus.data.NextDeparture
import com.senk.bus.data.entity.Schedule
import com.senk.bus.widget.BusWidgetProvider
import java.time.LocalDateTime

class AddEditScheduleFragment : Fragment() {

    private var routeId: Long = NO_ID
    private var scheduleId: Long = NO_ID
    private lateinit var timePicker: WheelTimePicker

    /** 用户是否已经滚动过轮盘：避免异步回填覆盖用户选择。 */
    private var timePickerTouched = false

    /** 防止保存按钮被快速双击重复提交。 */
    private var saving = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_add_edit_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        if (args != null) {
            routeId = args.getLong(ARG_ROUTE_ID, NO_ID)
            scheduleId = args.getLong(ARG_SCHEDULE_ID, NO_ID)
        }

        val isEdit = scheduleId != NO_ID

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(if (isEdit) R.string.edit_schedule else R.string.add_schedule)
        toolbar.setNavigationOnClickListener { navigateBack() }

        timePicker = WheelTimePicker(
            view.findViewById<WheelPicker>(R.id.wheel_hour),
            view.findViewById<WheelPicker>(R.id.wheel_minute),
            requireContext(),
        )

        val now = LocalDateTime.now()
        timePicker.setTime(now.hour, now.minute)
        timePicker.setOnTimeChangedListener { _, _ -> timePickerTouched = true }

        if (isEdit) {
            val appContext: Context = requireContext().applicationContext
            val hostActivity: Activity = requireActivity()
            val editScheduleId = scheduleId
            AppExecutors.diskIO {
                val loadedSchedule = try {
                    AppDatabase.getInstance(appContext).scheduleDao().getById(editScheduleId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load schedule $editScheduleId", e)
                    null
                }
                if (loadedSchedule == null
                    || !NextDeparture.isValid(loadedSchedule.departureTime)
                ) {
                    return@diskIO
                }
                val minutesOfDay = NextDeparture.parseMinutes(loadedSchedule.departureTime)
                val hour = minutesOfDay / 60
                val minute = minutesOfDay % 60
                hostActivity.runOnUiThread {
                    if (!isAdded() || getView() == null || timePickerTouched) {
                        return@runOnUiThread
                    }
                    timePicker.setTime(hour, minute)
                }
            }
        }

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId != R.id.action_save) {
                return@setOnMenuItemClickListener false
            }
            if (saving) {
                return@setOnMenuItemClickListener true
            }
            saving = true
            item.isEnabled = false
            val saveItem: MenuItem = item

            val departureTime = NextDeparture.format(
                timePicker.getHour() * 60 + timePicker.getMinute(),
            )
            val appContext: Context = requireContext().applicationContext
            val hostActivity: Activity = requireActivity()
            val editScheduleId = scheduleId
            val targetRouteId = routeId
            val successMessage = getString(
                if (isEdit) R.string.schedule_updated else R.string.schedule_saved,
            )
            val failureMessage = getString(R.string.save_failed)

            AppExecutors.diskIO {
                var success = true
                try {
                    val db = AppDatabase.getInstance(appContext)
                    if (isEdit) {
                        val schedule = db.scheduleDao().getById(editScheduleId)
                        if (schedule == null) {
                            success = false
                        } else {
                            schedule.departureTime = departureTime
                            db.scheduleDao().update(schedule)
                        }
                    } else {
                        val schedule = Schedule()
                        schedule.routeId = targetRouteId
                        schedule.departureTime = departureTime
                        db.scheduleDao().insert(schedule)
                    }
                    if (success) {
                        BusWidgetProvider.notifyDataChanged(appContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save schedule", e)
                    success = false
                }
                val saved = success
                hostActivity.runOnUiThread {
                    if (!isAdded() || getView() == null) {
                        return@runOnUiThread
                    }
                    if (saved) {
                        Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show()
                        navigateBack()
                    } else {
                        saving = false
                        saveItem.isEnabled = true
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }
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
        private const val TAG = "AddEditScheduleFragment"
        private const val ARG_ROUTE_ID = "route_id"
        private const val ARG_SCHEDULE_ID = "schedule_id"
        private const val NO_ID = -1L

        @JvmStatic
        fun newInstance(routeId: Long): AddEditScheduleFragment {
            return newInstance(routeId, NO_ID)
        }

        @JvmStatic
        fun newInstance(routeId: Long, scheduleId: Long): AddEditScheduleFragment {
            val fragment = AddEditScheduleFragment()
            val args = Bundle()
            args.putLong(ARG_ROUTE_ID, routeId)
            args.putLong(ARG_SCHEDULE_ID, scheduleId)
            fragment.arguments = args
            return fragment
        }
    }
}

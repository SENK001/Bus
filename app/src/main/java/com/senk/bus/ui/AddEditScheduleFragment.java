package com.senk.bus.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.NextDeparture;
import com.senk.bus.data.entity.Schedule;
import com.senk.bus.widget.BusWidgetProvider;

import java.time.LocalDateTime;

public class AddEditScheduleFragment extends Fragment {

    private static final String TAG = "AddEditScheduleFragment";
    private static final String ARG_ROUTE_ID = "route_id";
    private static final String ARG_SCHEDULE_ID = "schedule_id";
    private static final long NO_ID = -1;

    private Long routeId = NO_ID;
    private Long scheduleId = NO_ID;
    private WheelTimePicker timePicker;
    /** 用户是否已经滚动过轮盘：避免异步回填覆盖用户选择。 */
    private boolean timePickerTouched;
    /** 防止保存按钮被快速双击重复提交。 */
    private boolean saving;

    public static AddEditScheduleFragment newInstance(long routeId) {
        return newInstance(routeId, NO_ID);
    }

    public static AddEditScheduleFragment newInstance(long routeId, long scheduleId) {
        AddEditScheduleFragment fragment = new AddEditScheduleFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ROUTE_ID, routeId);
        args.putLong(ARG_SCHEDULE_ID, scheduleId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_edit_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            routeId = getArguments().getLong(ARG_ROUTE_ID, NO_ID);
            scheduleId = getArguments().getLong(ARG_SCHEDULE_ID, NO_ID);
        }

        final boolean isEdit = scheduleId != NO_ID;

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(isEdit ? R.string.edit_schedule : R.string.add_schedule);
        toolbar.setNavigationOnClickListener(v -> navigateBack());

        timePicker = new WheelTimePicker(
                view.findViewById(R.id.wheel_hour),
                view.findViewById(R.id.wheel_minute),
                requireContext());

        LocalDateTime now = LocalDateTime.now();
        timePicker.setTime(now.getHour(), now.getMinute());
        timePicker.setOnTimeChangedListener((hour, minute) -> timePickerTouched = true);

        if (isEdit) {
            final Context appContext = requireContext().getApplicationContext();
            final Activity hostActivity = requireActivity();
            final long editScheduleId = scheduleId;
            AppExecutors.diskIO(() -> {
                Schedule loadedSchedule = null;
                try {
                    loadedSchedule = AppDatabase.getInstance(appContext)
                            .scheduleDao().getById(editScheduleId);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load schedule " + editScheduleId, e);
                }
                if (loadedSchedule == null
                        || !NextDeparture.isValid(loadedSchedule.getDepartureTime())) {
                    return;
                }
                int minutesOfDay =
                        NextDeparture.parseMinutes(loadedSchedule.getDepartureTime());
                final int hour = minutesOfDay / 60;
                final int minute = minutesOfDay % 60;
                hostActivity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null || timePickerTouched) {
                        return;
                    }
                    timePicker.setTime(hour, minute);
                });
            });
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != R.id.action_save) {
                return false;
            }
            if (saving) {
                return true;
            }
            saving = true;
            item.setEnabled(false);
            final MenuItem saveItem = item;

            final String departureTime = NextDeparture.format(
                    timePicker.getHour() * 60 + timePicker.getMinute());
            final Context appContext = requireContext().getApplicationContext();
            final Activity hostActivity = requireActivity();
            final long editScheduleId = scheduleId;
            final long targetRouteId = routeId;
            final String successMessage =
                    getString(isEdit ? R.string.schedule_updated : R.string.schedule_saved);
            final String failureMessage = getString(R.string.save_failed);

            AppExecutors.diskIO(() -> {
                boolean success = true;
                try {
                    AppDatabase db = AppDatabase.getInstance(appContext);
                    if (isEdit) {
                        Schedule schedule = db.scheduleDao().getById(editScheduleId);
                        if (schedule == null) {
                            success = false;
                        } else {
                            schedule.setDepartureTime(departureTime);
                            db.scheduleDao().update(schedule);
                        }
                    } else {
                        Schedule schedule = new Schedule();
                        schedule.setRouteId(targetRouteId);
                        schedule.setDepartureTime(departureTime);
                        db.scheduleDao().insert(schedule);
                    }
                    if (success) {
                        BusWidgetProvider.notifyDataChanged(appContext);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save schedule", e);
                    success = false;
                }
                final boolean saved = success;
                hostActivity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    if (saved) {
                        Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show();
                        navigateBack();
                    } else {
                        saving = false;
                        saveItem.setEnabled(true);
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            });
            return true;
        });
    }

    private void navigateBack() {
        FragmentManager manager = getParentFragmentManager();
        if (manager.isStateSaved()) {
            return;
        }
        if (manager.getBackStackEntryCount() > 0) {
            manager.popBackStackImmediate();
        }
    }
}

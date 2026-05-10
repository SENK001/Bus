package com.senk.bus.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Schedule;

import java.time.LocalDateTime;
import java.util.Locale;

public class AddEditScheduleFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final String ARG_SCHEDULE_ID = "schedule_id";
    private static final long NO_ID = -1;

    private Long routeId;
    private Long scheduleId = NO_ID;
    private WheelTimePicker timePicker;

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
            routeId = getArguments().getLong(ARG_ROUTE_ID);
            scheduleId = getArguments().getLong(ARG_SCHEDULE_ID, NO_ID);
        }

        boolean isEdit = scheduleId != NO_ID;

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(isEdit ? R.string.edit_schedule : R.string.add_schedule);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        timePicker = new WheelTimePicker(
                view.findViewById(R.id.wheel_hour),
                view.findViewById(R.id.wheel_minute),
                requireContext());

        LocalDateTime time = LocalDateTime.now();
        timePicker.setTime(time.getHour(), time.getMinute());

        if (isEdit) {
            AppExecutors.diskIO(() -> {
                Schedule schedule = AppDatabase.getInstance(requireContext())
                        .scheduleDao().getById(scheduleId);
                if (schedule != null && getView() != null && schedule.departureTime.contains(":")) {
                    String[] parts = schedule.departureTime.split(":");
                    int h = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    requireActivity().runOnUiThread(() -> timePicker.setTime(h, m));
                }
            });
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                String departureTime = timePicker.getFormattedTime();

                if (isEdit) {
                    AppExecutors.diskIO(() -> {
                        Schedule schedule = AppDatabase.getInstance(requireContext())
                                .scheduleDao().getById(scheduleId);
                        if (schedule != null) {
                            schedule.departureTime = departureTime;
                            AppDatabase.getInstance(requireContext()).scheduleDao().update(schedule);
                        }
                    });
                } else {
                    Schedule schedule = new Schedule();
                    schedule.routeId = routeId;
                    schedule.departureTime = departureTime;
                    AppExecutors.diskIO(() -> AppDatabase.getInstance(requireContext()).scheduleDao().insert(schedule));
                }

                Toast.makeText(requireContext(),
                        isEdit ? R.string.schedule_updated : R.string.schedule_saved,
                        Toast.LENGTH_SHORT).show();
                getParentFragmentManager().popBackStack();
                return true;
            }
            return false;
        });
    }
}

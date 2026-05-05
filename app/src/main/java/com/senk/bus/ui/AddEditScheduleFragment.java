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
import com.itheima.wheelpicker.WheelPicker;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Schedule;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class AddEditScheduleFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final String ARG_SCHEDULE_ID = "schedule_id";
    private static final int NO_ID = -1;

    private int routeId;
    private int scheduleId = NO_ID;
    private WheelPicker wheelHour;
    private WheelPicker wheelMinute;
    private int hour;
    private int minute;

    public static AddEditScheduleFragment newInstance(int routeId) {
        return newInstance(routeId, NO_ID);
    }

    public static AddEditScheduleFragment newInstance(int routeId, int scheduleId) {
        AddEditScheduleFragment fragment = new AddEditScheduleFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ROUTE_ID, routeId);
        args.putInt(ARG_SCHEDULE_ID, scheduleId);
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
            routeId = getArguments().getInt(ARG_ROUTE_ID);
            scheduleId = getArguments().getInt(ARG_SCHEDULE_ID, NO_ID);
        }

        boolean isEdit = scheduleId != NO_ID;

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(isEdit ? R.string.edit_schedule : R.string.add_schedule);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        wheelHour = view.findViewById(R.id.wheel_hour);
        wheelMinute = view.findViewById(R.id.wheel_minute);

        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(String.format(Locale.getDefault(),"%02d", i));
        List<String> minutes = new ArrayList<>();
        for (int i = 0; i < 60; i++) minutes.add(String.format(Locale.getDefault(),"%02d", i));

        wheelHour.setData(hours);
        wheelMinute.setData(minutes);
        LocalDateTime time = LocalDateTime.now();
        hour = time.getHour();
        minute = time.getMinute();
        wheelHour.setSelectedItemPosition(hour);
        wheelMinute.setSelectedItemPosition(minute);

        wheelHour.setOnItemSelectedListener((picker, data, position) -> hour = position);
        wheelMinute.setOnItemSelectedListener((picker, data, position) -> minute = position);

        if (isEdit) {
            AppExecutors.diskIO(() -> {
                Schedule schedule = AppDatabase.getInstance(requireContext())
                        .scheduleDao().getById(scheduleId);
                if (schedule != null && getView() != null && schedule.departureTime.contains(":")) {
                    String[] parts = schedule.departureTime.split(":");
                    hour = Integer.parseInt(parts[0]);
                    minute = Integer.parseInt(parts[1]);
                    requireActivity().runOnUiThread(() -> {
                        wheelHour.setSelectedItemPosition(hour);
                        wheelMinute.setSelectedItemPosition(minute);
                    });
                }
            });
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                String departureTime = String.format(Locale.getDefault(),"%02d:%02d", hour, minute);

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

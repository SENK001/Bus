package com.senk.bus.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Schedule;

public class AddEditScheduleFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final String ARG_SCHEDULE_ID = "schedule_id";
    private static final int NO_ID = -1;

    private int routeId;
    private int scheduleId = NO_ID;
    private String departureTime;

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

        TextView pageTitle = view.findViewById(R.id.page_title);
        TextView tvDeparture = view.findViewById(R.id.tv_departure_time);

        boolean isEdit = scheduleId != NO_ID;
        pageTitle.setText(isEdit ? R.string.edit_schedule : R.string.add_schedule);

        if (isEdit) {
            AppExecutors.diskIO(() -> {
                Schedule schedule = AppDatabase.getInstance(requireContext())
                        .scheduleDao().getById(scheduleId);
                if (schedule != null && getView() != null) {
                    departureTime = schedule.departureTime;
                    requireActivity().runOnUiThread(() ->
                            tvDeparture.setText(departureTime));
                }
            });
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        view.findViewById(R.id.btn_pick_departure).setOnClickListener(v -> {
            int hour = 8, minute = 0;
            if (departureTime != null && departureTime.contains(":")) {
                String[] parts = departureTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(hour)
                    .setMinute(minute)
                    .setTitleText(R.string.pick_time)
                    .build();

            picker.addOnPositiveButtonClickListener(v2 -> {
                departureTime = String.format("%02d:%02d", picker.getHour(), picker.getMinute());
                tvDeparture.setText(departureTime);
            });

            picker.show(getChildFragmentManager(), "time_picker");
        });

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (departureTime == null || departureTime.isEmpty()) {
                Toast.makeText(requireContext(), R.string.set_departure_time, Toast.LENGTH_SHORT).show();
                return;
            }

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
                AppExecutors.diskIO(() -> {
                    AppDatabase.getInstance(requireContext()).scheduleDao().insert(schedule);
                });
            }

            Toast.makeText(requireContext(),
                    isEdit ? R.string.schedule_updated : R.string.schedule_saved,
                    Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStack();
        });
    }
}

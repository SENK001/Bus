package com.senk.bus.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Schedule;
import com.senk.bus.ui.adapter.ScheduleAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleListFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final String ARG_ROUTE_NAME = "route_name";
    private static final long REFRESH_INTERVAL = 60_000L;
    private static final int MAX_PADDING_DP = 50;
    private static final int MIN_PADDING_DP = 5;
    private static final float MAX_TEXT_SP = 20f;
    private static final float MIN_TEXT_SP = 14f;

    private long routeId;
    private ScheduleAdapter adapter;
    private TextView banner;
    private Handler handler;
    private Runnable refreshRunnable;

    public static ScheduleListFragment newInstance(long routeId, String routeName) {
        ScheduleListFragment fragment = new ScheduleListFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ROUTE_ID, routeId);
        args.putString(ARG_ROUTE_NAME, routeName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            routeId = getArguments().getLong(ARG_ROUTE_ID);
        }
        handler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        RecyclerView recyclerView = view.findViewById(R.id.schedule_recycler_view);
        banner = view.findViewById(R.id.next_departure_banner);
        TextView tvEmpty = view.findViewById(R.id.tv_empty_schedules);

        toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragment() instanceof QueryFragment) {
                getParentFragment().getChildFragmentManager().popBackStack();
            }
        });

        toolbar.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_schedule_list, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_add_schedule) {
                    QueryFragment parent = (QueryFragment) getParentFragment();
                    if (parent != null) {
                        parent.navigateToAddSchedule(routeId);
                    }
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        adapter = new ScheduleAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnScheduleClickListener(this::showEditDialog);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateBannerStyle(rv.computeVerticalScrollOffset());
            }
        });

        AppDatabase.getInstance(requireContext()).scheduleDao()
                .getSchedulesForRoute(routeId)
                .observe(getViewLifecycleOwner(), schedules -> {
                    List<Schedule> sorted = sortSchedulesByProximity(schedules);
                    adapter.setSchedules(sorted);

                    tvEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(sorted.isEmpty() ? View.GONE : View.VISIBLE);

                    updateBanner(sorted);
                });

        if (getArguments() != null) {
            toolbar.setTitle(getArguments().getString(ARG_ROUTE_NAME));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    private void startAutoRefresh() {
        refreshRunnable = () -> {
            if (adapter != null) {
                adapter.setSchedules(adapter.getCurrentSchedules());
                updateBanner(adapter.getCurrentSchedules());
            }
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
        };
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopAutoRefresh() {
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void showEditDialog(Schedule schedule) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_schedule, null);

        WheelTimePicker timePicker = new WheelTimePicker(
                dialogView.findViewById(R.id.wheel_hour),
                dialogView.findViewById(R.id.wheel_minute),
                requireContext());

        if (schedule.departureTime.contains(":")) {
            String[] parts = schedule.departureTime.split(":");
            timePicker.setTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        TextView tvSubtitle = dialogView.findViewById(R.id.tv_subtitle);
        tvSubtitle.setText(calcDepartIn(getNow(), schedule.departureTime));

        timePicker.setOnTimeChangedListener((h, m) ->
                tvSubtitle.setText(calcDepartIn(getNow(),
                        String.format(Locale.getDefault(), "%02d:%02d", h, m))));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete)
                    .setMessage(R.string.confirm_delete_schedule)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        AppExecutors.diskIO(() ->
                                AppDatabase.getInstance(requireContext()).scheduleDao().delete(schedule));
                        Toast.makeText(requireContext(), R.string.schedule_deleted, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String departureTime = timePicker.getFormattedTime();
            AppExecutors.diskIO(() -> {
                schedule.departureTime = departureTime;
                AppDatabase.getInstance(requireContext()).scheduleDao().update(schedule);
            });
            Toast.makeText(requireContext(), R.string.schedule_updated, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateBanner(List<Schedule> schedules) {
        String now = getNow();
        if (!schedules.isEmpty()) {
            Schedule s = schedules.get(0);
            String remaining = calcRemaining(now, s.departureTime);
            if (remaining.equals(getString(R.string.depart_now))) {
                banner.setText(remaining);
            } else {
                banner.setText(getString(R.string.next_departure_format, remaining));
            }
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void updateBannerStyle(int scrollY) {
        float density = getResources().getDisplayMetrics().density;
        float maxPadding = MAX_PADDING_DP * density;
        float minPadding = MIN_PADDING_DP * density;
        int maxScroll = (int) (100 * density);

        float fraction = Math.min(1f, scrollY / (float) maxScroll);
        int padding = (int) (maxPadding - fraction * (maxPadding - minPadding));
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) banner.getLayoutParams();
        lp.topMargin = padding;
        lp.bottomMargin = padding;
        banner.setLayoutParams(lp);

        if (fraction >= 1f) {
            float extraScroll = scrollY - maxScroll;
            float fontRange = (MAX_TEXT_SP - MIN_TEXT_SP) * density;
            float fontFraction = Math.min(1f, extraScroll / (100 * density));
            banner.setTextSize(20 - fontFraction * (MAX_TEXT_SP - MIN_TEXT_SP));
        } else {
            banner.setTextSize(MAX_TEXT_SP);
        }
    }

    private int diffMinutes(String from, String to) {
        String[] f = from.split(":");
        String[] t = to.split(":");
        int diff = (Integer.parseInt(t[0]) * 60 + Integer.parseInt(t[1]))
                 - (Integer.parseInt(f[0]) * 60 + Integer.parseInt(f[1]));
        if (diff < 0) diff += 24 * 60;
        return diff;
    }

    private String calcDepartIn(String from, String to) {
        int diff = diffMinutes(from, to);
        if (diff == 0) return getString(R.string.depart_now);
        int h = diff / 60;
        int m = diff % 60;
        if (h > 0) {
            return getString(R.string.depart_in_hm, h, m);
        }
        return getString(R.string.depart_in_m, m);
    }

    private String calcRemaining(String from, String to) {
        int diff = diffMinutes(from, to);
        if (diff == 0) return getString(R.string.depart_now);
        int h = diff / 60;
        int m = diff % 60;
        if (h > 0) {
            return getString(R.string.time_remaining_hm, h, m);
        }
        return getString(R.string.time_remaining_m, m);
    }

    private List<Schedule> sortSchedulesByProximity(List<Schedule> schedules) {
        String now = getNow();
        List<Schedule> sorted = new ArrayList<>(schedules);
        sorted.sort((a, b) -> Integer.compare(diffMinutes(now, a.departureTime), diffMinutes(now, b.departureTime)));
        return sorted;
    }

    private String getNow() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }
}

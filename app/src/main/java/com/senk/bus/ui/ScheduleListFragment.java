package com.senk.bus.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.NextDeparture;
import com.senk.bus.data.entity.Schedule;
import com.senk.bus.ui.adapter.ScheduleAdapter;
import com.senk.bus.widget.BusWidgetProvider;

import java.util.ArrayList;
import java.util.List;

public class ScheduleListFragment extends Fragment {

    private static final String TAG = "ScheduleListFragment";
    private static final String ARG_ROUTE_ID = "route_id";
    private static final String ARG_ROUTE_NAME = "route_name";
    /** 车次列表/横幅的自动刷新周期：每分钟重算一次「下一班」。 */
    private static final long REFRESH_INTERVAL = 60_000L;
    private static final int MAX_PADDING_DP = 50;
    private static final int MIN_PADDING_DP = 5;
    private static final float MAX_TEXT_SP = 20f;
    private static final float MIN_TEXT_SP = 14f;

    private long routeId;
    private ScheduleAdapter adapter;
    private RecyclerView recyclerView;
    private TextView banner;
    private TextView emptyView;
    private Handler handler;
    private Runnable refreshRunnable;
    /** 数据库中的车次（departureTime 升序），定时刷新时重新排序后使用。 */
    private List<Schedule> schedules = new ArrayList<>();

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
        recyclerView = view.findViewById(R.id.schedule_recycler_view);
        banner = view.findViewById(R.id.next_departure_banner);
        emptyView = view.findViewById(R.id.tv_empty_schedules);

        toolbar.setNavigationOnClickListener(v -> navigateBack());

        toolbar.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_schedule_list, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_add_schedule) {
                    QueryFragment parent = queryParent();
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
                .observe(getViewLifecycleOwner(), list -> {
                    schedules = list == null ? new ArrayList<>() : list;
                    renderSchedules();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
    }

    /**
     * 按「距下一班由近到远」重新排序后刷新列表与横幅，保证 NEXT 徽章始终是下一条车次。
     * 统一使用 {@link NextDeparture}，不再各自实现 diffMinutes。
     */
    private void renderSchedules() {
        if (!isAdded() || getView() == null || adapter == null || banner == null) {
            return;
        }
        String now = NextDeparture.nowHHmm();
        List<Schedule> sorted = sortByProximity(schedules, now);
        NextDeparture next = NextDeparture.find(now, sorted);
        adapter.setSchedules(sorted, next == null ? -1 : next.schedule.getId());
        updateBanner(next);
        boolean empty = sorted.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        refreshRunnable = () -> {
            renderSchedules();
            scheduleNextRefresh();
        };
        renderSchedules();
        scheduleNextRefresh();
    }

    /** 对齐到下一个整分钟刷新，避免倒计时/下一班最多滞后一整分钟。 */
    private void scheduleNextRefresh() {
        if (handler == null || refreshRunnable == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = REFRESH_INTERVAL - (now % REFRESH_INTERVAL);
        handler.postDelayed(refreshRunnable, delay);
    }

    private void stopAutoRefresh() {
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    private void showEditDialog(Schedule schedule) {
        if (!isAdded()) {
            return;
        }
        final long scheduleId = schedule.getId();
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_schedule, null);

        final WheelTimePicker timePicker = new WheelTimePicker(
                dialogView.findViewById(R.id.wheel_hour),
                dialogView.findViewById(R.id.wheel_minute),
                requireContext());

        int minutesOfDay = NextDeparture.parseMinutes(schedule.getDepartureTime());
        if (minutesOfDay < 0) {
            minutesOfDay = NextDeparture.parseMinutes(NextDeparture.nowHHmm());
        }
        if (minutesOfDay >= 0) {
            timePicker.setTime(minutesOfDay / 60, minutesOfDay % 60);
        }

        final TextView tvSubtitle = dialogView.findViewById(R.id.tv_subtitle);
        tvSubtitle.setText(departInText(
                NextDeparture.format(timePicker.getHour() * 60 + timePicker.getMinute())));

        timePicker.setOnTimeChangedListener((hour, minute) -> {
            tvSubtitle.setText(departInText(NextDeparture.format(hour * 60 + minute)));
        });

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        final View saveButton = dialogView.findViewById(R.id.btn_save);
        final boolean[] saveInFlight = {false};

        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.delete)
                        .setMessage(R.string.confirm_delete_schedule)
                        .setPositiveButton(R.string.delete,
                                (confirmDialog, which) -> deleteSchedule(scheduleId, dialog))
                        .setNegativeButton(R.string.cancel, null)
                        .show());

        saveButton.setOnClickListener(v -> {
            if (saveInFlight[0]) {
                return;
            }
            saveInFlight[0] = true;
            saveButton.setEnabled(false);
            saveSchedule(scheduleId, NextDeparture.format(
                    timePicker.getHour() * 60 + timePicker.getMinute()),
                    dialog, saveButton, saveInFlight);
        });

        dialog.show();
    }

    private void saveSchedule(long scheduleId, String departureTime, AlertDialog dialog,
                              View saveButton, boolean[] saveInFlight) {
        saveInFlight[0] = true;
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final String successMessage = getString(R.string.schedule_updated);
        final String failureMessage = getString(R.string.save_failed);

        AppExecutors.diskIO(() -> {
            boolean success = true;
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                Schedule entity = db.scheduleDao().getById(scheduleId);
                if (entity == null) {
                    success = false;
                } else {
                    entity.setDepartureTime(departureTime);
                    db.scheduleDao().update(entity);
                    BusWidgetProvider.notifyDataChanged(appContext);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update schedule " + scheduleId, e);
                success = false;
            }
            final boolean updated = success;
            hostActivity.runOnUiThread(() -> {
                if (updated) {
                    if (isAdded()) {
                        Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show();
                    }
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                } else {
                    saveInFlight[0] = false;
                    saveButton.setEnabled(true);
                    if (isAdded()) {
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    private void deleteSchedule(long scheduleId, AlertDialog dialog) {
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final String successMessage = getString(R.string.schedule_deleted);
        final String failureMessage = getString(R.string.delete_failed);

        AppExecutors.diskIO(() -> {
            boolean success = true;
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                Schedule entity = db.scheduleDao().getById(scheduleId);
                if (entity != null) {
                    db.scheduleDao().delete(entity);
                }
                BusWidgetProvider.notifyDataChanged(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete schedule " + scheduleId, e);
                success = false;
            }
            final boolean deleted = success;
            hostActivity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (deleted) {
                    Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show();
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                } else {
                    Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void updateBanner(@Nullable NextDeparture next) {
        if (next == null) {
            banner.setVisibility(View.GONE);
            return;
        }
        String remaining = remainingText(next.remainingMinutes);
        if (next.remainingMinutes == 0) {
            banner.setText(remaining);
        } else {
            banner.setText(getString(R.string.next_departure_format, remaining));
        }
        banner.setVisibility(View.VISIBLE);
    }

    private String remainingText(int remainingMinutes) {
        if (remainingMinutes <= 0) {
            return getString(R.string.depart_now);
        }
        int hours = remainingMinutes / 60;
        int minutes = remainingMinutes % 60;
        if (hours > 0) {
            return getString(R.string.time_remaining_hm, hours, minutes);
        }
        return getString(R.string.time_remaining_m, minutes);
    }

    /** 编辑弹窗副标题：「N 小时后发车」；时间非法时返回空串。 */
    private String departInText(String departureTime) {
        int remaining = NextDeparture.minutesUntil(NextDeparture.nowHHmm(), departureTime);
        if (remaining < 0) {
            return "";
        }
        if (remaining == 0) {
            return getString(R.string.depart_now);
        }
        int hours = remaining / 60;
        int minutes = remaining % 60;
        if (hours > 0) {
            return getString(R.string.depart_in_hm, hours, minutes);
        }
        return getString(R.string.depart_in_m, minutes);
    }

    private void updateBannerStyle(int scrollY) {
        if (banner == null) {
            return;
        }
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
            float fontFraction = Math.min(1f, extraScroll / (100 * density));
            banner.setTextSize(MAX_TEXT_SP - fontFraction * (MAX_TEXT_SP - MIN_TEXT_SP));
        } else {
            banner.setTextSize(MAX_TEXT_SP);
        }
    }

    /**
     * 按「从现在开始的剩余分钟数」升序排序；非法时间排到最后。
     * 输入为 DAO 的 departureTime 升序列表，排序后第一条即下一班。
     */
    private static List<Schedule> sortByProximity(List<Schedule> source, String now) {
        List<Schedule> sorted = new ArrayList<>(source);
        sorted.sort((first, second) -> {
            int firstRemaining = NextDeparture.minutesUntil(now, first.getDepartureTime());
            int secondRemaining = NextDeparture.minutesUntil(now, second.getDepartureTime());
            if (firstRemaining < 0 || secondRemaining < 0) {
                if (firstRemaining < 0 && secondRemaining < 0) {
                    return 0;
                }
                return firstRemaining < 0 ? 1 : -1;
            }
            if (firstRemaining != secondRemaining) {
                return Integer.compare(firstRemaining, secondRemaining);
            }
            return first.getDepartureTime().compareTo(second.getDepartureTime());
        });
        return sorted;
    }

    @Nullable
    private QueryFragment queryParent() {
        Fragment parent = getParentFragment();
        return parent instanceof QueryFragment ? (QueryFragment) parent : null;
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

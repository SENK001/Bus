package com.senk.bus.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private static final long REFRESH_INTERVAL = 60_000L;

    private int routeId;
    private ScheduleAdapter adapter;
    private TextView banner;
    private Handler handler;
    private Runnable refreshRunnable;

    public static ScheduleListFragment newInstance(int routeId) {
        ScheduleListFragment fragment = new ScheduleListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ROUTE_ID, routeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            routeId = getArguments().getInt(ARG_ROUTE_ID);
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

        RecyclerView recyclerView = view.findViewById(R.id.schedule_recycler_view);
        banner = view.findViewById(R.id.next_departure_banner);
        TextView tvEmpty = view.findViewById(R.id.tv_empty_schedules);
        FloatingActionButton fab = view.findViewById(R.id.fab_add_schedule);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getParentFragment() instanceof QueryFragment) {
                getParentFragment().getChildFragmentManager().popBackStack();
            }
        });

        adapter = new ScheduleAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnScheduleLongClickListener((schedule, anchor) -> showPopupMenu(schedule, anchor));

        fab.setOnClickListener(v -> {
            QueryFragment parent = (QueryFragment) getParentFragment();
            if (parent != null) {
                parent.navigateToAddSchedule(routeId);
            }
        });

        AppDatabase.getInstance(requireContext()).scheduleDao()
                .getSchedulesForRoute(routeId)
                .observe(getViewLifecycleOwner(), schedules -> {
                    List<Schedule> sorted = sortSchedulesByProximity(schedules);
                    adapter.setSchedules(sorted);

                    tvEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(sorted.isEmpty() ? View.GONE : View.VISIBLE);
                    fab.setVisibility(View.VISIBLE);

                    updateBanner(sorted);
                });

        // Load route name for title
        AppExecutors.diskIO(() -> {
            com.senk.bus.data.entity.Route route =
                    AppDatabase.getInstance(requireContext()).routeDao().getById(routeId);
            if (route != null && getView() != null) {
                requireActivity().runOnUiThread(() -> {
                    TextView title = getView().findViewById(R.id.route_title);
                    if (title != null) title.setText(route.name);
                });
            }
        });
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

    private void showPopupMenu(Schedule schedule, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.edit);
        popup.getMenu().add(0, 2, 1, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) {
                QueryFragment parent = (QueryFragment) getParentFragment();
                if (parent != null) {
                    parent.navigateToEditSchedule(routeId, schedule.id);
                }
                return true;
            } else if (itemId == 2) {
                deleteSchedule(schedule);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void deleteSchedule(Schedule schedule) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(R.string.confirm_delete_schedule)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AppExecutors.diskIO(() ->
                            AppDatabase.getInstance(requireContext()).scheduleDao().delete(schedule));
                    Toast.makeText(requireContext(), R.string.schedule_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateBanner(List<Schedule> schedules) {
        String now = getNow();
        for (Schedule s : schedules) {
            if (s.departureTime.compareTo(now) >= 0) {
                banner.setText(getString(R.string.next_departure_format, s.departureTime));
                banner.setVisibility(View.VISIBLE);
                return;
            }
        }
        banner.setVisibility(View.GONE);
    }

    private List<Schedule> sortSchedulesByProximity(List<Schedule> schedules) {
        String now = getNow();
        List<Schedule> future = new ArrayList<>();
        List<Schedule> past = new ArrayList<>();
        for (Schedule s : schedules) {
            if (s.departureTime.compareTo(now) >= 0) {
                future.add(s);
            } else {
                past.add(s);
            }
        }
        List<Schedule> result = new ArrayList<>(future);
        result.addAll(past);
        return result;
    }

    private String getNow() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }
}

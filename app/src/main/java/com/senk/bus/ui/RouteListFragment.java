package com.senk.bus.ui;

import android.os.Bundle;
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
import com.senk.bus.data.entity.Route;
import com.senk.bus.data.entity.Schedule;
import com.senk.bus.ui.adapter.RouteAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RouteListFragment extends Fragment {

    private RouteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_route_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.route_recycler_view);
        TextView tvEmpty = view.findViewById(R.id.tv_empty);
        FloatingActionButton fab = view.findViewById(R.id.fab_add_route);

        adapter = new RouteAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnRouteClickListener(route -> {
            QueryFragment parent = (QueryFragment) getParentFragment();
            if (parent != null) {
                parent.navigateToScheduleList(route.id);
            }
        });

        adapter.setOnRouteLongClickListener((route, anchor) -> showPopupMenu(route, anchor));

        fab.setOnClickListener(v -> {
            QueryFragment parent = (QueryFragment) getParentFragment();
            if (parent != null) {
                parent.navigateToAddRoute();
            }
        });

        AppDatabase.getInstance(requireContext()).routeDao().getAllRoutes()
                .observe(getViewLifecycleOwner(), routes -> {
                    adapter.setRoutes(routes);
                    tvEmpty.setVisibility(routes.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(routes.isEmpty() ? View.GONE : View.VISIBLE);
                    fab.setVisibility(View.VISIBLE);
                    loadNextTimes(routes);
                });
    }

    private void loadNextTimes(List<Route> routes) {
        AppExecutors.diskIO(() -> {
            String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            AppDatabase db = AppDatabase.getInstance(requireContext());
            for (Route route : routes) {
                List<Schedule> schedules = db.scheduleDao().getSchedulesForRouteSync(route.id);
                String next = null;
                // Find first un-departed schedule
                for (Schedule s : schedules) {
                    if (s.departureTime.compareTo(now) >= 0) {
                        next = s.departureTime;
                        break;
                    }
                }
                // If all past, pick the earliest (tomorrow's first)
                if (next == null && !schedules.isEmpty()) {
                    next = schedules.get(0).departureTime;
                }
                final String nextTime = next;
                if (nextTime != null) {
                    requireActivity().runOnUiThread(() -> {
                        adapter.setNextTime(route.id, nextTime);
                    });
                }
            }
            requireActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        });
    }

    private void showPopupMenu(Route route, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, route.isFavorite ? R.string.unfavorite : R.string.favorite);
        popup.getMenu().add(0, 2, 1, route.isDefault ? R.string.unset_default : R.string.set_default);
        popup.getMenu().add(0, 3, 2, R.string.edit);
        popup.getMenu().add(0, 4, 3, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) {
                AppExecutors.diskIO(() ->
                        AppDatabase.getInstance(requireContext())
                                .routeDao().setFavorite(route.id, !route.isFavorite));
                return true;
            } else if (itemId == 2) {
                if (route.isDefault) {
                    AppExecutors.diskIO(() ->
                            AppDatabase.getInstance(requireContext())
                                    .routeDao().clearAllDefaults());
                } else {
                    AppExecutors.diskIO(() ->
                            AppDatabase.getInstance(requireContext())
                                    .routeDao().setDefault(route.id));
                }
                return true;
            } else if (itemId == 3) {
                QueryFragment parent = (QueryFragment) getParentFragment();
                if (parent != null) {
                    parent.navigateToEditRoute(route.id);
                }
                return true;
            } else if (itemId == 4) {
                deleteRoute(route);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void deleteRoute(Route route) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_delete_route, route.name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    AppExecutors.diskIO(() ->
                            AppDatabase.getInstance(requireContext()).routeDao().delete(route));
                    Toast.makeText(requireContext(),
                            getString(R.string.route_deleted, route.name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

package com.senk.bus.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
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
import com.senk.bus.data.entity.Route;
import com.senk.bus.data.entity.Schedule;
import com.senk.bus.ui.adapter.RouteAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        RecyclerView recyclerView = view.findViewById(R.id.route_recycler_view);
        TextView tvEmpty = view.findViewById(R.id.tv_empty);

        toolbar.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_add) {
                    QueryFragment parent = (QueryFragment) getParentFragment();
                    if (parent != null) {
                        parent.navigateToAddRoute();
                    }
                    return true;
                } else if (id == R.id.action_export) {
                    exportData();
                    return true;
                } else if (id == R.id.action_import) {
                    importData();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        adapter = new RouteAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnRouteClickListener(route -> {
            QueryFragment parent = (QueryFragment) getParentFragment();
            if (parent != null) {
                parent.navigateToScheduleList(route.id, route.name);
            }
        });

        adapter.setOnRouteLongClickListener((route, anchor) -> showPopupMenu(route, anchor));

        AppDatabase.getInstance(requireContext()).routeDao().getAllRoutes()
                .observe(getViewLifecycleOwner(), routes -> {
                    adapter.setRoutes(routes);
                    tvEmpty.setVisibility(routes.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(routes.isEmpty() ? View.GONE : View.VISIBLE);
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
                for (Schedule s : schedules) {
                    if (s.departureTime.compareTo(now) >= 0) {
                        next = s.departureTime;
                        break;
                    }
                }
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
                    AppExecutors.diskIO(() ->{
                                AppDatabase.getInstance(requireContext()).routeDao().delete(route);
                                AppDatabase.getInstance(requireContext()).scheduleDao().deleteByRouteId(route.id);
                            });
                    Toast.makeText(requireContext(),
                            getString(R.string.route_deleted, route.name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportData() {
        AppExecutors.diskIO(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                List<Route> routes = db.routeDao().getAllRoutesSync();

                JSONArray routesArray = new JSONArray();

                for (Route route : routes) {
                    JSONObject routeObj = new JSONObject();
                    routeObj.put("name", route.name);
                    routeObj.put("origin", route.origin);
                    routeObj.put("destination", route.destination);
                    routeObj.put("isFavorite", route.isFavorite);
                    routeObj.put("isDefault", route.isDefault);

                    List<Schedule> schedules = db.scheduleDao().getSchedulesForRouteSync(route.id);
                    JSONArray schedulesArray = new JSONArray();
                    for (Schedule schedule : schedules) {
                        JSONObject scheduleObj = new JSONObject();
                        scheduleObj.put("departureTime", schedule.departureTime);
                        schedulesArray.put(scheduleObj);
                    }
                    routeObj.put("schedules", schedulesArray);
                    routesArray.put(routeObj);
                }

                JSONObject data = new JSONObject();
                data.put("routes", routesArray);

                String json = data.toString();
                String base64 = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

                requireActivity().runOnUiThread(() -> {
                    ClipboardManager clipboard = (ClipboardManager) requireActivity()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("bus_data", base64));
                    Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.import_error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void importData() {
        ClipboardManager clipboard = (ClipboardManager) requireActivity()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (!clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(requireContext(), R.string.import_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String text = clipboard.getPrimaryClip().getItemAt(0).getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), R.string.import_error, Toast.LENGTH_SHORT).show();
            return;
        }

        AppExecutors.diskIO(() -> {
            try {
                byte[] decoded = Base64.decode(text, Base64.NO_WRAP);
                String json = new String(decoded, StandardCharsets.UTF_8);
                JSONObject data = new JSONObject(json);

                AppDatabase db = AppDatabase.getInstance(requireContext());
                JSONArray routesArray = data.getJSONArray("routes");

                for (int i = 0; i < routesArray.length(); i++) {
                    JSONObject routeObj = routesArray.getJSONObject(i);
                    Route route = new Route();
                    route.name = routeObj.getString("name");
                    route.origin = routeObj.getString("origin");
                    route.destination = routeObj.getString("destination");
                    route.isFavorite = routeObj.optBoolean("isFavorite", false);
                    route.isDefault = routeObj.optBoolean("isDefault", false);
                    long newId = db.routeDao().insert(route);

                    JSONArray schedulesArray = routeObj.getJSONArray("schedules");
                    for (int j = 0; j < schedulesArray.length(); j++) {
                        JSONObject scheduleObj = schedulesArray.getJSONObject(j);
                        Schedule schedule = new Schedule();
                        schedule.routeId = newId;
                        schedule.departureTime = scheduleObj.getString("departureTime");
                        db.scheduleDao().insert(schedule);
                    }
                }

                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.import_error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}

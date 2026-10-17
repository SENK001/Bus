package com.senk.bus.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
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
import com.senk.bus.data.NextDeparture;
import com.senk.bus.data.entity.Route;
import com.senk.bus.data.entity.Schedule;
import com.senk.bus.ui.adapter.RouteAdapter;
import com.senk.bus.widget.BusWidgetProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RouteListFragment extends Fragment {

    private static final String TAG = "RouteListFragment";
    /** 路线卡片「下一班」的自动刷新周期：每分钟按当前时间重算一次。 */
    private static final long MINUTE_REFRESH_INTERVAL = 60_000L;
    private static final String CLIP_LABEL = "bus_data";
    /** 导出 JSON 结构版本；格式变化时必须递增并在导入侧兼容旧版本。 */
    private static final int EXPORT_VERSION = 2;

    private RouteAdapter adapter;
    private Handler handler;
    private Runnable minuteTicker;
    /** 最近一次 LiveData 下发的路线列表（下发给磁盘线程重算下一班用）。 */
    private List<Route> routes = new ArrayList<>();
    private boolean importing;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
    }

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
                    QueryFragment parent = queryParent();
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
            QueryFragment parent = queryParent();
            if (parent != null) {
                parent.navigateToScheduleList(route.getId(), route.getName());
            }
        });

        adapter.setOnRouteLongClickListener(this::showPopupMenu);

        AppDatabase.getInstance(requireContext()).routeDao().getAllRoutes()
                .observe(getViewLifecycleOwner(), list -> {
                    routes = list == null ? new ArrayList<>() : list;
                    adapter.setRoutes(routes);
                    tvEmpty.setVisibility(routes.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(routes.isEmpty() ? View.GONE : View.VISIBLE);
                    loadNextTimes();
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        startMinuteTicker();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMinuteTicker();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMinuteTicker();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    private void startMinuteTicker() {
        stopMinuteTicker();
        minuteTicker = () -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            loadNextTimes();
            scheduleNextMinuteTick();
        };
        if (!routes.isEmpty()) {
            // 从其它页面返回时立即刷新，避免旧数据停留到下一个整分钟。
            loadNextTimes();
        }
        scheduleNextMinuteTick();
    }

    /** 对齐到下一个整分钟触发，保证下一班时间最多滞后一次刷新周期。 */
    private void scheduleNextMinuteTick() {
        if (handler == null || minuteTicker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long delay = MINUTE_REFRESH_INTERVAL - (now % MINUTE_REFRESH_INTERVAL);
        handler.postDelayed(minuteTicker, delay);
    }

    private void stopMinuteTicker() {
        if (handler != null && minuteTicker != null) {
            handler.removeCallbacks(minuteTicker);
        }
    }

    /**
     * 磁盘线程一次查出全部车次，按路线分组后用 {@link NextDeparture} 统一计算下一班，
     * 再回主线程批量更新 adapter，避免每路线一次回调、也避免后台线程碰 Fragment API。
     */
    private void loadNextTimes() {
        if (adapter == null) {
            return;
        }
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final List<Route> currentRoutes = new ArrayList<>(routes);
        if (currentRoutes.isEmpty()) {
            adapter.setNextTimes(Collections.emptyMap());
            return;
        }
        AppExecutors.diskIO(() -> {
            Map<Long, String> nextTimes = new HashMap<>();
            boolean success = true;
            try {
                String now = NextDeparture.nowHHmm();
                List<Schedule> allSchedules = AppDatabase.getInstance(appContext)
                        .scheduleDao().getAllSchedulesSync();
                Map<Long, List<Schedule>> schedulesByRoute = new HashMap<>();
                for (Schedule schedule : allSchedules) {
                    List<Schedule> routeSchedules = schedulesByRoute.get(schedule.getRouteId());
                    if (routeSchedules == null) {
                        routeSchedules = new ArrayList<>();
                        schedulesByRoute.put(schedule.getRouteId(), routeSchedules);
                    }
                    routeSchedules.add(schedule);
                }
                for (Route route : currentRoutes) {
                    NextDeparture next = NextDeparture.find(
                            now, schedulesByRoute.get(route.getId()));
                    if (next != null) {
                        nextTimes.put(route.getId(), next.time);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load next departure times", e);
                success = false;
            }
            if (!success) {
                return;
            }
            final Map<Long, String> result = nextTimes;
            hostActivity.runOnUiThread(() -> {
                if (!isAdded() || adapter == null) {
                    return;
                }
                adapter.setNextTimes(result);
            });
        });
    }

    private void showPopupMenu(Route route, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, route.isFavorite() ? R.string.unfavorite : R.string.favorite);
        popup.getMenu().add(0, 2, 1, route.isDefault() ? R.string.unset_default : R.string.set_default);
        popup.getMenu().add(0, 3, 2, R.string.edit);
        popup.getMenu().add(0, 4, 3, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == 1) {
                toggleFavorite(route);
                return true;
            } else if (itemId == 2) {
                toggleDefault(route);
                return true;
            } else if (itemId == 3) {
                QueryFragment parent = queryParent();
                if (parent != null) {
                    parent.navigateToEditRoute(route.getId());
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

    /** 收藏/取消收藏：写库成功后才提示，失败回主线程提示错误。 */
    private void toggleFavorite(Route route) {
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final long routeId = route.getId();
        final boolean favorite = !route.isFavorite();
        final String successMessage = getString(favorite
                ? R.string.favorite_added : R.string.favorite_removed);
        final String failureMessage = getString(R.string.operation_failed);
        AppExecutors.diskIO(() -> {
            boolean success = true;
            try {
                AppDatabase.getInstance(appContext).routeDao().setFavorite(routeId, favorite);
                BusWidgetProvider.notifyDataChanged(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to toggle favorite for route " + routeId, e);
                success = false;
            }
            final boolean updated = success;
            hostActivity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(appContext, updated ? successMessage : failureMessage,
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** 设为/取消默认：写库成功后才提示，失败提示并保留原状态。 */
    private void toggleDefault(Route route) {
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final long routeId = route.getId();
        final boolean wasDefault = route.isDefault();
        final String successMessage = getString(wasDefault
                ? R.string.default_unset : R.string.default_set);
        final String failureMessage = getString(R.string.operation_failed);
        AppExecutors.diskIO(() -> {
            boolean success = true;
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                if (wasDefault) {
                    db.routeDao().clearAllDefaults();
                } else {
                    db.routeDao().setDefault(routeId);
                }
                BusWidgetProvider.notifyDataChanged(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to toggle default for route " + routeId, e);
                success = false;
            }
            final boolean updated = success;
            hostActivity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(appContext, updated ? successMessage : failureMessage,
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void deleteRoute(Route route) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_delete_route, route.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> performDeleteRoute(route))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 删除成功才提示成功；车次由外键 CASCADE 级联删除。 */
    private void performDeleteRoute(Route route) {
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final long routeId = route.getId();
        final String successMessage = getString(R.string.route_deleted, route.getName());
        final String failureMessage = getString(R.string.delete_failed);
        AppExecutors.diskIO(() -> {
            boolean success = true;
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                Route entity = db.routeDao().getById(routeId);
                if (entity != null) {
                    db.routeDao().delete(entity);
                }
                BusWidgetProvider.notifyDataChanged(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete route " + routeId, e);
                success = false;
            }
            final boolean deleted = success;
            hostActivity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(appContext,
                        deleted ? successMessage : failureMessage,
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ---------------------------------------------------------------------
    // 导出 / 导入
    // ---------------------------------------------------------------------

    /**
     * 导出为 Base64(JSON)：{"version":2,"routes":[{name,origin,destination,isFavorite,
     * isDefault,schedules:[{departureTime}]}]}，复制到剪贴板。
     */
    private void exportData() {
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        final String successMessage = getString(R.string.export_success);
        final String failureMessage = getString(R.string.export_error);
        AppExecutors.diskIO(() -> {
            String payload = null;
            try {
                payload = buildExportPayload(appContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to build export payload", e);
            }
            final String base64 = payload;
            hostActivity.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (base64 == null) {
                    Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    ClipboardManager clipboard = (ClipboardManager) appContext
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard == null) {
                        throw new IllegalStateException("ClipboardManager unavailable");
                    }
                    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, base64));
                    Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to copy export data to clipboard", e);
                    Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static String buildExportPayload(Context appContext) throws JSONException {
        AppDatabase db = AppDatabase.getInstance(appContext);
        List<Route> routes = db.routeDao().getAllRoutesSync();
        List<Schedule> allSchedules = db.scheduleDao().getAllSchedulesSync();
        Map<Long, List<Schedule>> schedulesByRoute = new HashMap<>();
        for (Schedule schedule : allSchedules) {
            List<Schedule> routeSchedules = schedulesByRoute.get(schedule.getRouteId());
            if (routeSchedules == null) {
                routeSchedules = new ArrayList<>();
                schedulesByRoute.put(schedule.getRouteId(), routeSchedules);
            }
            routeSchedules.add(schedule);
        }

        JSONArray routesArray = new JSONArray();
        for (Route route : routes) {
            JSONObject routeObject = new JSONObject();
            routeObject.put("name", route.getName());
            routeObject.put("origin", route.getOrigin());
            routeObject.put("destination", route.getDestination());
            routeObject.put("isFavorite", route.isFavorite());
            routeObject.put("isDefault", route.isDefault());

            JSONArray schedulesArray = new JSONArray();
            List<Schedule> routeSchedules = schedulesByRoute.get(route.getId());
            if (routeSchedules != null) {
                for (Schedule schedule : routeSchedules) {
                    JSONObject scheduleObject = new JSONObject();
                    scheduleObject.put("departureTime", schedule.getDepartureTime());
                    schedulesArray.put(scheduleObject);
                }
            }
            routeObject.put("schedules", schedulesArray);
            routesArray.put(routeObject);
        }

        JSONObject data = new JSONObject();
        data.put("version", EXPORT_VERSION);
        data.put("routes", routesArray);
        return Base64.encodeToString(
                data.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    /**
     * 从剪贴板导入：Base64 解码容错、字段与时间严格校验、整段导入在单个 Room 事务内完成，
     * 并按 name+origin+destination 去重合并，重复导入不会产生整份重复数据。
     */
    private void importData() {
        if (importing) {
            return;
        }
        final String failureMessage = getString(R.string.import_error);
        String clipboardText = null;
        try {
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                showToast(failureMessage);
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                showToast(failureMessage);
                return;
            }
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null) {
                showToast(failureMessage);
                return;
            }
            clipboardText = text.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read clipboard for import", e);
            showToast(failureMessage);
            return;
        }
        if (clipboardText.isEmpty()) {
            showToast(failureMessage);
            return;
        }

        importing = true;
        final String base64 = clipboardText;
        final Context appContext = requireContext().getApplicationContext();
        final Activity hostActivity = requireActivity();
        AppExecutors.diskIO(() -> {
            int[] stats = null;
            try {
                byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                String json = new String(decoded, StandardCharsets.UTF_8);
                JSONObject data = new JSONObject(json);
                JSONArray routesArray = data.optJSONArray("routes");
                if (routesArray == null) {
                    throw new JSONException("Import payload has no routes array");
                }
                stats = importIntoDatabase(AppDatabase.getInstance(appContext), routesArray);
                try {
                    BusWidgetProvider.notifyDataChanged(appContext);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to refresh widget after import", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
            }
            final int[] result = stats;
            hostActivity.runOnUiThread(() -> {
                importing = false;
                if (!isAdded()) {
                    return;
                }
                if (result == null) {
                    Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(appContext, appContext.getString(
                                    R.string.import_result, result[0], result[1]),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 在单个事务内写入导入数据。
     *
     * @return {@code [0] = 成功处理的路线数, [1] = 跳过的无效数据条数}
     */
    private static int[] importIntoDatabase(AppDatabase db, JSONArray routesArray) {
        final int[] stats = new int[2];
        db.runInTransaction(() -> {
            // isDefault 归一化：先清空现有默认，再让导入项中第一条 isDefault=true 生效。
            db.routeDao().clearAllDefaults();
            List<Route> knownRoutes = new ArrayList<>(db.routeDao().getAllRoutesSync());
            boolean defaultAssigned = false;

            for (int i = 0; i < routesArray.length(); i++) {
                JSONObject routeObject = routesArray.optJSONObject(i);
                if (routeObject == null) {
                    stats[1]++;
                    continue;
                }
                String name = readText(routeObject, "name");
                String origin = readText(routeObject, "origin");
                String destination = readText(routeObject, "destination");
                if (name.isEmpty() || origin.isEmpty() || destination.isEmpty()) {
                    stats[1]++;
                    continue;
                }

                List<String> validTimes = new ArrayList<>();
                JSONArray schedulesArray = routeObject.optJSONArray("schedules");
                if (schedulesArray != null) {
                    for (int j = 0; j < schedulesArray.length(); j++) {
                        String departureTime = readDepartureTime(
                                schedulesArray.optJSONObject(j));
                        if (departureTime != null) {
                            validTimes.add(departureTime);
                        } else {
                            stats[1]++;
                        }
                    }
                }

                boolean favorite = routeObject.optBoolean("isFavorite", false);
                boolean wantsDefault =
                        routeObject.optBoolean("isDefault", false) && !defaultAssigned;

                Route route = findExistingRoute(knownRoutes, name, origin, destination);
                if (route == null) {
                    route = new Route();
                    route.setName(name);
                    route.setOrigin(origin);
                    route.setDestination(destination);
                    route.setFavorite(favorite);
                    route.setDefault(wantsDefault);
                    long newId = db.routeDao().insert(route);
                    route.setId(newId);
                    knownRoutes.add(route);
                } else {
                    boolean changed = false;
                    if (favorite && !route.isFavorite()) {
                        route.setFavorite(true);
                        changed = true;
                    }
                    if (wantsDefault && !route.isDefault()) {
                        route.setDefault(true);
                        changed = true;
                    }
                    if (changed) {
                        db.routeDao().update(route);
                    }
                }
                if (wantsDefault) {
                    defaultAssigned = true;
                }

                Set<String> existingTimes = new HashSet<>();
                for (Schedule existing
                        : db.scheduleDao().getSchedulesForRouteSync(route.getId())) {
                    existingTimes.add(existing.getDepartureTime());
                }
                for (String departureTime : validTimes) {
                    if (!existingTimes.add(departureTime)) {
                        // 同一导入数据内重复、或数据库已存在该时间：跳过，保证幂等。
                        continue;
                    }
                    Schedule schedule = new Schedule();
                    schedule.setRouteId(route.getId());
                    schedule.setDepartureTime(departureTime);
                    db.scheduleDao().insert(schedule);
                }
                stats[0]++;
            }
        });
        return stats;
    }

    @Nullable
    private static Route findExistingRoute(List<Route> routes, String name, String origin,
                                           String destination) {
        for (Route route : routes) {
            if (route != null
                    && name.equals(route.getName())
                    && origin.equals(route.getOrigin())
                    && destination.equals(route.getDestination())) {
                return route;
            }
        }
        return null;
    }

    private static String readText(@Nullable JSONObject object, String key) {
        if (object == null) {
            return "";
        }
        Object value = object.opt(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    @Nullable
    private static String readDepartureTime(@Nullable JSONObject object) {
        String departureTime = readText(object, "departureTime");
        return NextDeparture.isValid(departureTime) ? departureTime : null;
    }

    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private QueryFragment queryParent() {
        Fragment parent = getParentFragment();
        return parent instanceof QueryFragment ? (QueryFragment) parent : null;
    }
}

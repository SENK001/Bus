package com.senk.bus.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Route;
import com.senk.bus.widget.BusWidgetProvider;

public class AddEditRouteFragment extends Fragment {

    private static final String TAG = "AddEditRouteFragment";
    private static final String ARG_ROUTE_ID = "route_id";
    private static final long NO_ID = -1;

    private Long routeId = NO_ID;
    /** 防止保存按钮被快速双击重复提交。 */
    private boolean saving;

    public static AddEditRouteFragment newInstance() {
        return new AddEditRouteFragment();
    }

    public static AddEditRouteFragment newInstance(long routeId) {
        AddEditRouteFragment fragment = new AddEditRouteFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ROUTE_ID, routeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_edit_route, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            routeId = getArguments().getLong(ARG_ROUTE_ID, NO_ID);
        }

        final boolean isEdit = routeId != NO_ID;

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(isEdit ? R.string.edit_route : R.string.add_route);
        toolbar.setNavigationOnClickListener(v -> navigateBack());

        final EditText etName = view.findViewById(R.id.et_route_name);
        final EditText etOrigin = view.findViewById(R.id.et_origin);
        final EditText etDestination = view.findViewById(R.id.et_destination);

        if (isEdit) {
            final Context appContext = requireContext().getApplicationContext();
            final Activity hostActivity = requireActivity();
            final long editRouteId = routeId;
            AppExecutors.diskIO(() -> {
                Route loadedRoute = null;
                try {
                    loadedRoute = AppDatabase.getInstance(appContext)
                            .routeDao().getById(editRouteId);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load route " + editRouteId, e);
                }
                if (loadedRoute == null) {
                    return;
                }
                final Route route = loadedRoute;
                hostActivity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    // 只在输入框为空时回填：配置变更恢复的用户输入不会被 DB 旧值覆盖。
                    if (etName.getText().length() == 0) {
                        etName.setText(route.getName());
                    }
                    if (etOrigin.getText().length() == 0) {
                        etOrigin.setText(route.getOrigin());
                    }
                    if (etDestination.getText().length() == 0) {
                        etDestination.setText(route.getDestination());
                    }
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

            String name = etName.getText().toString().trim();
            String origin = etOrigin.getText().toString().trim();
            String destination = etDestination.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError(getString(R.string.name_required));
                return true;
            }
            if (origin.isEmpty()) {
                etOrigin.setError(getString(R.string.origin_required));
                return true;
            }
            if (destination.isEmpty()) {
                etDestination.setError(getString(R.string.destination_required));
                return true;
            }

            saving = true;
            item.setEnabled(false);
            final MenuItem saveItem = item;
            final Context appContext = requireContext().getApplicationContext();
            final Activity hostActivity = requireActivity();
            final long editRouteId = routeId;
            final String successMessage =
                    getString(isEdit ? R.string.route_updated : R.string.route_saved);
            final String failureMessage = getString(R.string.save_failed);

            AppExecutors.diskIO(() -> {
                boolean success = true;
                try {
                    AppDatabase db = AppDatabase.getInstance(appContext);
                    if (isEdit) {
                        Route route = db.routeDao().getById(editRouteId);
                        if (route == null) {
                            success = false;
                        } else {
                            route.setName(name);
                            route.setOrigin(origin);
                            route.setDestination(destination);
                            db.routeDao().update(route);
                        }
                    } else {
                        Route route = new Route();
                        route.setName(name);
                        route.setOrigin(origin);
                        route.setDestination(destination);
                        db.routeDao().insert(route);
                    }
                    if (success) {
                        BusWidgetProvider.notifyDataChanged(appContext);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save route", e);
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

package com.senk.bus.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Route;

public class AddEditRouteFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final long NO_ID = -1;

    private Long routeId = NO_ID;

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

        boolean isEdit = routeId != NO_ID;

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(isEdit ? R.string.edit_route : R.string.add_route);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        EditText etName = view.findViewById(R.id.et_route_name);
        EditText etOrigin = view.findViewById(R.id.et_origin);
        EditText etDestination = view.findViewById(R.id.et_destination);

        if (isEdit) {
            AppExecutors.diskIO(() -> {
                Route route = AppDatabase.getInstance(requireContext()).routeDao().getById(routeId);
                if (route != null && getView() != null) {
                    requireActivity().runOnUiThread(() -> {
                        etName.setText(route.getName());
                        etOrigin.setText(route.getOrigin());
                        etDestination.setText(route.getDestination());
                    });
                }
            });
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
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

                if (isEdit) {
                    AppExecutors.diskIO(() -> {
                        Route route = AppDatabase.getInstance(requireContext()).routeDao().getById(routeId);
                        if (route != null) {
                            route.setName(name);
                            route.setOrigin(origin);
                            route.setDestination(destination);
                            AppDatabase.getInstance(requireContext()).routeDao().update(route);
                        }
                    });
                } else {
                    Route route = new Route();
                    route.setName(name);
                    route.setOrigin(origin);
                    route.setDestination(destination);
                    AppExecutors.diskIO(() -> {
                        AppDatabase.getInstance(requireContext()).routeDao().insert(route);
                    });
                }

                Toast.makeText(requireContext(),
                        isEdit ? R.string.route_updated : R.string.route_saved,
                        Toast.LENGTH_SHORT).show();
                getParentFragmentManager().popBackStack();
                return true;
            }
            return false;
        });
    }
}

package com.senk.bus.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.senk.bus.R;
import com.senk.bus.data.AppDatabase;
import com.senk.bus.data.AppExecutors;
import com.senk.bus.data.entity.Route;

public class AddEditRouteFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";
    private static final int NO_ID = -1;

    private int routeId = NO_ID;

    public static AddEditRouteFragment newInstance() {
        return new AddEditRouteFragment();
    }

    public static AddEditRouteFragment newInstance(int routeId) {
        AddEditRouteFragment fragment = new AddEditRouteFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_ROUTE_ID, routeId);
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
            routeId = getArguments().getInt(ARG_ROUTE_ID, NO_ID);
        }

        EditText etName = view.findViewById(R.id.et_route_name);
        EditText etOrigin = view.findViewById(R.id.et_origin);
        EditText etDestination = view.findViewById(R.id.et_destination);
        TextView pageTitle = view.findViewById(R.id.page_title);

        boolean isEdit = routeId != NO_ID;
        pageTitle.setText(isEdit ? R.string.edit_route : R.string.add_route);

        if (isEdit) {
            AppExecutors.diskIO(() -> {
                Route route = AppDatabase.getInstance(requireContext()).routeDao().getById(routeId);
                if (route != null && getView() != null) {
                    requireActivity().runOnUiThread(() -> {
                        etName.setText(route.name);
                        etOrigin.setText(route.origin);
                        etDestination.setText(route.destination);
                    });
                }
            });
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String origin = etOrigin.getText().toString().trim();
            String destination = etDestination.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError(getString(R.string.name_required));
                return;
            }
            if (origin.isEmpty()) {
                etOrigin.setError(getString(R.string.origin_required));
                return;
            }
            if (destination.isEmpty()) {
                etDestination.setError(getString(R.string.destination_required));
                return;
            }

            if (isEdit) {
                AppExecutors.diskIO(() -> {
                    Route route = AppDatabase.getInstance(requireContext()).routeDao().getById(routeId);
                    if (route != null) {
                        route.name = name;
                        route.origin = origin;
                        route.destination = destination;
                        AppDatabase.getInstance(requireContext()).routeDao().update(route);
                    }
                });
            } else {
                Route route = new Route();
                route.name = name;
                route.origin = origin;
                route.destination = destination;
                AppExecutors.diskIO(() -> {
                    AppDatabase.getInstance(requireContext()).routeDao().insert(route);
                });
            }

            Toast.makeText(requireContext(),
                    isEdit ? R.string.route_updated : R.string.route_saved,
                    Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStack();
        });
    }
}

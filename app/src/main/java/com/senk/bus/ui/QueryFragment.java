package com.senk.bus.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.senk.bus.R;

public class QueryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_query, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.query_container, new RouteListFragment())
                    .commit();
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
                            getChildFragmentManager().popBackStack();
                        } else {
                            requireActivity().finish();
                        }
                    }
                }
        );
    }

    public void navigateToScheduleList(long routeId, String routeName) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.query_container, ScheduleListFragment.newInstance(routeId, routeName))
                .addToBackStack(null)
                .commit();
    }

    public void navigateToAddRoute() {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.query_container, AddEditRouteFragment.newInstance())
                .addToBackStack(null)
                .commit();
    }

    public void navigateToEditRoute(long routeId) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.query_container, AddEditRouteFragment.newInstance(routeId))
                .addToBackStack(null)
                .commit();
    }

    public void navigateToAddSchedule(long routeId) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.query_container, AddEditScheduleFragment.newInstance(routeId))
                .addToBackStack(null)
                .commit();
    }

    public void navigateToEditSchedule(long routeId, long scheduleId) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.query_container, AddEditScheduleFragment.newInstance(routeId, scheduleId))
                .addToBackStack(null)
                .commit();
    }
}

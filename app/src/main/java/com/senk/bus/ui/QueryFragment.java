package com.senk.bus.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.senk.bus.R;

public class QueryFragment extends Fragment {

    /** 导航入口防抖窗口：避免快速双击压入两个相同页面。 */
    private static final long NAVIGATION_DEBOUNCE_MS = 500L;

    private long lastNavigationAt;

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
                        FragmentManager childManager = getChildFragmentManager();
                        if (childManager.isStateSaved()) {
                            // 已保存状态（即将重建）时不能立即出栈，避免 IllegalStateException。
                            return;
                        }
                        if (childManager.getBackStackEntryCount() > 0) {
                            // popBackStackImmediate 同步执行：出栈后 count 立即更新，快速连按返回键
                            // 时第二次按键基于最新 count 判断，不会一次弹出两层。
                            childManager.popBackStackImmediate();
                            return;
                        }
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.finish();
                        }
                    }
                }
        );
    }

    public void navigateToScheduleList(long routeId, String routeName) {
        navigateTo(ScheduleListFragment.newInstance(routeId, routeName));
    }

    public void navigateToAddRoute() {
        navigateTo(AddEditRouteFragment.newInstance());
    }

    public void navigateToEditRoute(long routeId) {
        navigateTo(AddEditRouteFragment.newInstance(routeId));
    }

    public void navigateToAddSchedule(long routeId) {
        navigateTo(AddEditScheduleFragment.newInstance(routeId));
    }

    public void navigateToEditSchedule(long routeId, long scheduleId) {
        navigateTo(AddEditScheduleFragment.newInstance(routeId, scheduleId));
    }

    /**
     * 统一的子页面导航入口：500ms 时间戳防抖，避免快速双击/连点把同一个页面压栈两次。
     */
    private void navigateTo(Fragment fragment) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNavigationAt < NAVIGATION_DEBOUNCE_MS) {
            return;
        }
        FragmentManager childManager = getChildFragmentManager();
        if (childManager.isStateSaved()) {
            return;
        }
        lastNavigationAt = now;
        childManager.beginTransaction()
                .replace(R.id.query_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}

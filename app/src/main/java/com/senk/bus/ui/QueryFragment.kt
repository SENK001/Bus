package com.senk.bus.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.senk.bus.R

class QueryFragment : Fragment() {

    private var lastNavigationAt: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_query, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager
                .beginTransaction()
                .replace(R.id.query_container, RouteListFragment())
                .commit()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val childManager = childFragmentManager
                    if (childManager.isStateSaved) {
                        // 已保存状态（即将重建）时不能立即出栈，避免 IllegalStateException。
                        return
                    }
                    if (childManager.backStackEntryCount > 0) {
                        // popBackStackImmediate 同步执行：出栈后 count 立即更新，快速连按返回键
                        // 时第二次按键基于最新 count 判断，不会一次弹出两层。
                        childManager.popBackStackImmediate()
                        return
                    }
                    activity?.finish()
                }
            },
        )
    }

    fun navigateToScheduleList(routeId: Long, routeName: String) {
        navigateTo(ScheduleListFragment.newInstance(routeId, routeName))
    }

    fun navigateToAddRoute() {
        navigateTo(AddEditRouteFragment.newInstance())
    }

    fun navigateToEditRoute(routeId: Long) {
        navigateTo(AddEditRouteFragment.newInstance(routeId))
    }

    fun navigateToAddSchedule(routeId: Long) {
        navigateTo(AddEditScheduleFragment.newInstance(routeId))
    }

    fun navigateToEditSchedule(routeId: Long, scheduleId: Long) {
        navigateTo(AddEditScheduleFragment.newInstance(routeId, scheduleId))
    }

    /**
     * 统一的子页面导航入口：500ms 时间戳防抖，避免快速双击/连点把同一个页面压栈两次。
     */
    private fun navigateTo(fragment: Fragment) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNavigationAt < NAVIGATION_DEBOUNCE_MS) {
            return
        }
        val childManager = childFragmentManager
        if (childManager.isStateSaved) {
            return
        }
        lastNavigationAt = now
        childManager.beginTransaction()
            .replace(R.id.query_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        /** 导航入口防抖窗口：避免快速双击压入两个相同页面。 */
        private const val NAVIGATION_DEBOUNCE_MS = 500L
    }
}

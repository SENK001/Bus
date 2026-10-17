package com.senk.bus.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.MaterialToolbar
import com.senk.bus.R
import com.senk.bus.data.AppDatabase
import com.senk.bus.data.AppExecutors
import com.senk.bus.data.entity.Route
import com.senk.bus.widget.BusWidgetProvider

class AddEditRouteFragment : Fragment() {

    private var routeId: Long = NO_ID

    /** 防止保存按钮被快速双击重复提交。 */
    private var saving = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_add_edit_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        if (args != null) {
            routeId = args.getLong(ARG_ROUTE_ID, NO_ID)
        }

        val isEdit = routeId != NO_ID

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(if (isEdit) R.string.edit_route else R.string.add_route)
        toolbar.setNavigationOnClickListener { navigateBack() }

        val etName = view.findViewById<EditText>(R.id.et_route_name)
        val etOrigin = view.findViewById<EditText>(R.id.et_origin)
        val etDestination = view.findViewById<EditText>(R.id.et_destination)

        if (isEdit) {
            val appContext: Context = requireContext().applicationContext
            val hostActivity: Activity = requireActivity()
            val editRouteId = routeId
            AppExecutors.diskIO {
                val loadedRoute = try {
                    AppDatabase.getInstance(appContext).routeDao().getById(editRouteId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load route $editRouteId", e)
                    null
                }
                val route = loadedRoute ?: return@diskIO
                hostActivity.runOnUiThread {
                    if (!isAdded() || getView() == null) {
                        return@runOnUiThread
                    }
                    // 只在输入框为空时回填：配置变更恢复的用户输入不会被 DB 旧值覆盖。
                    if (etName.text.length == 0) {
                        etName.setText(route.name)
                    }
                    if (etOrigin.text.length == 0) {
                        etOrigin.setText(route.origin)
                    }
                    if (etDestination.text.length == 0) {
                        etDestination.setText(route.destination)
                    }
                }
            }
        }

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId != R.id.action_save) {
                return@setOnMenuItemClickListener false
            }
            if (saving) {
                return@setOnMenuItemClickListener true
            }

            val name = etName.text.toString().trim()
            val origin = etOrigin.text.toString().trim()
            val destination = etDestination.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = getString(R.string.name_required)
                return@setOnMenuItemClickListener true
            }
            if (origin.isEmpty()) {
                etOrigin.error = getString(R.string.origin_required)
                return@setOnMenuItemClickListener true
            }
            if (destination.isEmpty()) {
                etDestination.error = getString(R.string.destination_required)
                return@setOnMenuItemClickListener true
            }

            saving = true
            item.isEnabled = false
            val saveItem: MenuItem = item
            val appContext: Context = requireContext().applicationContext
            val hostActivity: Activity = requireActivity()
            val editRouteId = routeId
            val successMessage = getString(
                if (isEdit) R.string.route_updated else R.string.route_saved,
            )
            val failureMessage = getString(R.string.save_failed)

            AppExecutors.diskIO {
                var success = true
                try {
                    val db = AppDatabase.getInstance(appContext)
                    if (isEdit) {
                        val route = db.routeDao().getById(editRouteId)
                        if (route == null) {
                            success = false
                        } else {
                            route.name = name
                            route.origin = origin
                            route.destination = destination
                            db.routeDao().update(route)
                        }
                    } else {
                        val route = Route()
                        route.name = name
                        route.origin = origin
                        route.destination = destination
                        db.routeDao().insert(route)
                    }
                    if (success) {
                        BusWidgetProvider.notifyDataChanged(appContext)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save route", e)
                    success = false
                }
                val saved = success
                hostActivity.runOnUiThread {
                    if (!isAdded() || getView() == null) {
                        return@runOnUiThread
                    }
                    if (saved) {
                        Toast.makeText(appContext, successMessage, Toast.LENGTH_SHORT).show()
                        navigateBack()
                    } else {
                        saving = false
                        saveItem.isEnabled = true
                        Toast.makeText(appContext, failureMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }
    }

    private fun navigateBack() {
        val manager: FragmentManager = parentFragmentManager
        if (manager.isStateSaved) {
            return
        }
        if (manager.backStackEntryCount > 0) {
            manager.popBackStackImmediate()
        }
    }

    companion object {
        private const val TAG = "AddEditRouteFragment"
        private const val ARG_ROUTE_ID = "route_id"
        private const val NO_ID = -1L

        @JvmStatic
        fun newInstance(): AddEditRouteFragment {
            return AddEditRouteFragment()
        }

        @JvmStatic
        fun newInstance(routeId: Long): AddEditRouteFragment {
            val fragment = AddEditRouteFragment()
            val args = Bundle()
            args.putLong(ARG_ROUTE_ID, routeId)
            fragment.arguments = args
            return fragment
        }
    }
}

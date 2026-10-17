package com.senk.bus.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.senk.bus.data.entity.Route

@Dao
abstract class RouteDao {

    @Query("SELECT * FROM routes ORDER BY isDefault DESC, isFavorite DESC, id DESC")
    abstract fun getAllRoutes(): LiveData<List<Route>>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract fun getById(id: Long): Route?

    @Insert
    abstract fun insert(route: Route): Long

    @Update
    abstract fun update(route: Route)

    @Delete
    abstract fun delete(route: Route)

    @Query("UPDATE routes SET isDefault = 0")
    abstract fun clearAllDefaults()

    @Query("UPDATE routes SET isFavorite = :fav WHERE id = :routeId")
    abstract fun setFavorite(routeId: Long, fav: Boolean)

    @Query("SELECT * FROM routes ORDER BY isDefault DESC, isFavorite DESC, id DESC")
    abstract fun getAllRoutesSync(): List<Route>

    @Transaction
    open fun setDefault(routeId: Long) {
        clearAllDefaults()
        val route = getById(routeId) ?: return
        route.isDefault = true
        update(route)
    }
}

package com.senk.bus.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.senk.bus.data.entity.Route;

import java.util.List;

@Dao
public abstract class RouteDao {
    @Query("SELECT * FROM routes ORDER BY isDefault DESC, isFavorite DESC, id DESC")
    public abstract LiveData<List<Route>> getAllRoutes();

    @Query("SELECT * FROM routes WHERE id = :id")
    public abstract Route getById(long id);

    @Insert
    public abstract long insert(Route route);

    @Update
    public abstract void update(Route route);

    @Delete
    public abstract void delete(Route route);

    @Query("UPDATE routes SET isDefault = 0")
    public abstract void clearAllDefaults();

    @Query("UPDATE routes SET isFavorite = :fav WHERE id = :routeId")
    public abstract void setFavorite(long routeId, boolean fav);

    @Query("SELECT * FROM routes WHERE isDefault = 1 LIMIT 1")
    public abstract Route getDefaultRoute();

    @Query("SELECT * FROM routes")
    public abstract List<Route> getAllRoutesSync();

    @Transaction
    public void setDefault(long routeId) {
        clearAllDefaults();
        Route route = getById(routeId);
        if (route != null) {
            route.isDefault = true;
            update(route);
        }
    }
}

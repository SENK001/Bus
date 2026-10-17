package com.senk.bus.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.senk.bus.data.entity.Schedule;

import java.util.List;

@Dao
public interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE routeId = :routeId ORDER BY departureTime ASC")
    LiveData<List<Schedule>> getSchedulesForRoute(long routeId);

    @Query("SELECT * FROM schedules WHERE routeId = :routeId ORDER BY departureTime ASC")
    List<Schedule> getSchedulesForRouteSync(long routeId);

    /** All schedules for every route, ordered for grouping by routeId. */
    @Query("SELECT * FROM schedules ORDER BY routeId ASC, departureTime ASC")
    List<Schedule> getAllSchedulesSync();

    @Query("SELECT * FROM schedules WHERE id = :id")
    Schedule getById(long id);

    @Insert
    long insert(Schedule schedule);

    @Update
    void update(Schedule schedule);

    @Delete
    void delete(Schedule schedule);

}

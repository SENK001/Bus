package com.senk.bus.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.senk.bus.data.entity.Schedule;

import java.util.List;

@Dao
public interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE routeId = :routeId ORDER BY departureTime ASC")
    LiveData<List<Schedule>> getSchedulesForRoute(int routeId);

    @Query("SELECT * FROM schedules WHERE routeId = :routeId ORDER BY departureTime ASC")
    List<Schedule> getSchedulesForRouteSync(int routeId);

    @Query("SELECT * FROM schedules WHERE id = :id")
    Schedule getById(int id);

    @Insert
    long insert(Schedule schedule);

    @Update
    void update(Schedule schedule);

    @Delete
    void delete(Schedule schedule);

    @Query("DELETE FROM schedules WHERE routeId = :routeId")
    void deleteByRouteId(int routeId);
}

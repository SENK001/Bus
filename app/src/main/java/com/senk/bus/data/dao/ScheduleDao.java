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

    @Query("SELECT * FROM schedules WHERE id = :id")
    Schedule getById(long id);

    @Insert
    long insert(Schedule schedule);

    @Update
    void update(Schedule schedule);

    @Delete
    void delete(Schedule schedule);

    @Query("DELETE FROM schedules WHERE routeId = :routeId")
    void deleteByRouteId(long routeId);
}

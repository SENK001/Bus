package com.senk.bus.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.senk.bus.data.entity.Schedule

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules WHERE routeId = :routeId ORDER BY departureTime ASC")
    fun getSchedulesForRoute(routeId: Long): LiveData<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE routeId = :routeId ORDER BY departureTime ASC")
    fun getSchedulesForRouteSync(routeId: Long): List<Schedule>

    /** All schedules for every route, ordered for grouping by routeId. */
    @Query("SELECT * FROM schedules ORDER BY routeId ASC, departureTime ASC")
    fun getAllSchedulesSync(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id")
    fun getById(id: Long): Schedule?

    @Insert
    fun insert(schedule: Schedule): Long

    @Update
    fun update(schedule: Schedule)

    @Delete
    fun delete(schedule: Schedule)
}

package com.senk.bus.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [ForeignKey(
        entity = Route::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routeId")]
)
class Schedule {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var routeId: Long = 0

    var departureTime: String = "00:00"
}
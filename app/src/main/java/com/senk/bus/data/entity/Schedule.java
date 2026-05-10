package com.senk.bus.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "schedules",
        foreignKeys = @ForeignKey(
                entity = Route.class,
                parentColumns = "id",
                childColumns = "routeId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("routeId")}
)
public class Schedule {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long routeId;

    @NonNull
    public String departureTime = "00:00";
}

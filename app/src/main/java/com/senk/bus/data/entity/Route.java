package com.senk.bus.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routes")
public class Route {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String name;

    @NonNull
    public String origin;

    @NonNull
    public String destination;

    public boolean isFavorite;

    public boolean isDefault;
}

package com.senk.bus.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
class Route {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var name: String = ""

    var origin: String = ""

    var destination: String = ""

    var isFavorite: Boolean = false

    var isDefault: Boolean = false
}

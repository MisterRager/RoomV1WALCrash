package com.example.roomwalcrash

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class Thing(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String)
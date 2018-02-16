package com.example.roomwalcrash

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase

@Database(entities = [Thing::class], version = 1)
abstract class Db : RoomDatabase()
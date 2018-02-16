package com.example.roomwalcrash

import android.app.Application
import android.arch.persistence.room.Room
import android.util.Log

class App : Application() {
    private val db by lazy { Room.databaseBuilder(this, Db::class.java, "craaaashing").build() }

    override fun onCreate() {
        super.onCreate()
        db.openHelper.setWriteAheadLoggingEnabled(true)
        db.query("SELECT 1", arrayOf()).use {
            Log.d(javaClass.simpleName, "This will never actually happen.")
        }
    }
}
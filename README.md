# Room Doesn't WAL

The first release of Room, `1.0.0`, doesn't play nice with the write-ahead log in SQLite. On starting the application, calls to `RoomOpenHelper#createMasterTableIfNotExists` end up with some odd threadsy issues. When called in `#onCreate`, the table is created as expected, but things get weird when it's called a second time in `#onUpdate`:

```
02-15 18:52:46.204 16468-16468/com.example.roomwalcrash E/AndroidRuntime: FATAL EXCEPTION: main
                                                                          Process: com.example.roomwalcrash, PID: 16468
                                                                          java.lang.RuntimeException: Unable to create application com.example.roomwalcrash.App: android.database.sqlite.SQLiteException: Cannot execute this statement because it might modify the database but the connection is read-only.
                                                                              at android.app.ActivityThread.handleBindApplication(ActivityThread.java:5743)
                                                                              at android.app.ActivityThread.-wrap1(Unknown Source:0)
                                                                              at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1656)
                                                                              at android.os.Handler.dispatchMessage(Handler.java:106)
                                                                              at android.os.Looper.loop(Looper.java:164)
                                                                              at android.app.ActivityThread.main(ActivityThread.java:6494)
                                                                              at java.lang.reflect.Method.invoke(Native Method)
                                                                              at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438)
                                                                              at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
                                                                           Caused by: android.database.sqlite.SQLiteException: Cannot execute this statement because it might modify the database but the connection is read-only.
                                                                              at android.database.sqlite.SQLiteConnection.throwIfStatementForbidden(SQLiteConnection.java:1026)
                                                                              at android.database.sqlite.SQLiteConnection.executeForChangedRowCount(SQLiteConnection.java:730)
                                                                              at android.database.sqlite.SQLiteSession.executeForChangedRowCount(SQLiteSession.java:754)
                                                                              at android.database.sqlite.SQLiteStatement.executeUpdateDelete(SQLiteStatement.java:64)
                                                                              at android.database.sqlite.SQLiteDatabase.executeSql(SQLiteDatabase.java:1754)
                                                                              at android.database.sqlite.SQLiteDatabase.execSQL(SQLiteDatabase.java:1682)
                                                                              at android.arch.persistence.db.framework.FrameworkSQLiteDatabase.execSQL(FrameworkSQLiteDatabase.java:240)
                                                                              at android.arch.persistence.room.RoomOpenHelper.createMasterTableIfNotExists(RoomOpenHelper.java:131)
                                                                              at android.arch.persistence.room.RoomOpenHelper.checkIdentity(RoomOpenHelper.java:107)
                                                                              at android.arch.persistence.room.RoomOpenHelper.onOpen(RoomOpenHelper.java:100)
                                                                              at android.arch.persistence.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.onOpen(FrameworkSQLiteOpenHelper.java:133)
                                                                              at android.database.sqlite.SQLiteOpenHelper.getDatabaseLocked(SQLiteOpenHelper.java:349)
                                                                              at android.database.sqlite.SQLiteOpenHelper.getWritableDatabase(SQLiteOpenHelper.java:238)
                                                                              at android.arch.persistence.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getWritableSupportDatabase(FrameworkSQLiteOpenHelper.java:93)
                                                                              at android.arch.persistence.db.framework.FrameworkSQLiteOpenHelper.getWritableDatabase(FrameworkSQLiteOpenHelper.java:54)
                                                                              at android.arch.persistence.room.RoomDatabase.query(RoomDatabase.java:182)
                                                                              at com.example.roomwalcrash.App.onCreate(App.kt:13)
```

It looks like there are two separate checks to see if any given statement is will write to the database file or not, but with the write-ahead log enabled, not all of them will return the same result. The first happens in `SQLiteProgram` in the constructor:

```
...
     db.getThreadSession().prepare(mSql,
            db.getThreadDefaultConnectionFlags(assumeReadOnly),
            cancellationSignalForPrepare, info);
     mReadOnly = info.readOnly;
...
```

I followed the calls down to where this actually touches SQLite and found `SQLiteConnection#acquirePreparedStatement`:


```
    private PreparedStatement acquirePreparedStatement(String sql) {
        ...
        final long statementPtr = nativePrepareStatement(mConnectionPtr, sql);
        try {
            final int numParameters = nativeGetParameterCount(mConnectionPtr, statementPtr);
            final int type = DatabaseUtils.getSqlStatementType(sql);
            final boolean readOnly = nativeIsReadOnly(mConnectionPtr, statementPtr);
        ...
    }
```

This is the same place that the query is compiled for running when `SQLiteStatement#executeUpdateDelete` is called on the instance of `SQLiteStatement` constructed to insert the `room_master_table`. The difference is that the connection flags are to get a read-only connection when the statement is actually being run vs getting a read/write connection for when it's been compiled in the constructor of `SQLiteProgram`. I assume that because the write-ahead log allows for simultaneous reading and writing by allowing for more than one `SQLiteConnection`, the connection returned by `SQLiteSession#acquireConnection` is actually different with read-only vs read/write flags. Because of this, the query which checks out as read-only with the read/write connection is erroneously marked as a writing query when returned from `SQLiteConnection#acquirePreparedStatement` on the read-only connection.

package com.duorecord.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.duorecord.domain.*

internal class ClipDatabase(context: Context) : SQLiteOpenHelper(context, "clips.sqlite", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE clips(id TEXT PRIMARY KEY, uri TEXT UNIQUE NOT NULL, name TEXT NOT NULL, start_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, bytes INTEGER NOT NULL, kind TEXT NOT NULL, status TEXT NOT NULL, source_id TEXT, camera TEXT NOT NULL DEFAULT 'LEGACY', session_id TEXT, segment_index INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX clips_timeline ON clips(start_ms)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE clips ADD COLUMN camera TEXT NOT NULL DEFAULT 'LEGACY'")
            db.execSQL("ALTER TABLE clips ADD COLUMN session_id TEXT")
            db.execSQL("ALTER TABLE clips ADD COLUMN segment_index INTEGER NOT NULL DEFAULT 0")
        }
    }
    fun put(c: Clip) {
        val v=ContentValues().apply {
            put("id",c.id);put("uri",c.uri);put("name",c.name);put("start_ms",c.startMs)
            put("duration_ms",c.durationMs);put("bytes",c.bytes);put("kind",c.kind.name)
            put("status",c.status.name);put("source_id",c.sourceId);put("camera",c.camera.name);put("session_id",c.sessionId);put("segment_index",c.segmentIndex)
        }
        check(writableDatabase.insertWithOnConflict("clips",null,v,SQLiteDatabase.CONFLICT_REPLACE)!=-1L) { "Could not save video metadata." }
    }
    fun all(): List<Clip> = read("SELECT * FROM clips ORDER BY start_ms DESC",null)
    fun find(id:String):Clip?=read("SELECT * FROM clips WHERE id=?",arrayOf(id)).firstOrNull()
    fun byUri(uri:String):Clip?=read("SELECT * FROM clips WHERE uri=?",arrayOf(uri)).firstOrNull()
    private fun read(sql:String,args:Array<String>?):List<Clip> = readableDatabase.rawQuery(sql,args).use { c ->
        buildList { while(c.moveToNext()) add(Clip(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getLong(5),ClipKind.valueOf(c.getString(6)),ClipStatus.valueOf(c.getString(7)),c.getString(8),CameraSource.valueOf(c.getString(9)),c.getString(10),c.getInt(11))) }
    }
    fun delete(id:String) { writableDatabase.delete("clips","id=?",arrayOf(id)) }
}

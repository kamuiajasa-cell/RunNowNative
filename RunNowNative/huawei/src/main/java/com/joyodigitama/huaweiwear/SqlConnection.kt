package com.joyodigitama.huaweiwear

import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement

/**
 * What [WearDatabase] needs from a SQLite connection. The app's is [AndroidSqlConnection] (android.database.sqlite); the
 * JVM unit tests of JoyoFit used sqlite-jdbc. Failures are `android.database.sqlite.SQLiteException`.
 *
 * In PolarRun this file replaces ExpoSqlConnection.kt: there is no JS side and no second copy of SQLite in the process,
 * so Android's own SQLite is the right library here.
 */
internal interface SqlConnection {
  /** Runs one statement without parameters or results. */
  fun exec(sql: String)

  fun prepare(sql: String): SqlStatement

  /** Rows changed by the most recently completed INSERT, UPDATE or DELETE. */
  fun changes(): Int

  fun lastInsertRowId(): Long

  /** Closes the connection; the statements still open are finalized with it. */
  fun close()
}

internal interface SqlStatement {
  /** Binds null, Long, Int, Double, String or ByteArray to the 1-based [index]. */
  fun bind(index: Int, value: Any?)

  fun clearBindings()

  fun reset()

  /** True while the statement produced a row, false at the end of the result. */
  fun step(): Boolean

  fun columnCount(): Int

  /** The current row: Long, Double, String, ByteArray or null for each column. */
  fun row(): List<Any?>

  /** Finalizes the statement. */
  fun close()

  /** Frees a statement that [SqlConnection.close] finalized already. */
  fun release()
}

private fun keywordOf(sql: String): String = sql.trimStart().takeWhile { it.isLetter() }.uppercase()

/** [SqlConnection] over [SQLiteDatabase]. One instance is used by one [WearDatabase], which serializes access. */
internal class AndroidSqlConnection private constructor(private val db: SQLiteDatabase) : SqlConnection {
  private var lastChanges = 0
  private var lastRowId = 0L

  override fun exec(sql: String) {
    when (keywordOf(sql)) {
      // WearDatabase issues BEGIN IMMEDIATE / COMMIT / ROLLBACK itself; Android keeps its own transaction state, so map them.
      "BEGIN" -> db.beginTransactionNonExclusive()
      "COMMIT", "END" -> {
        db.setTransactionSuccessful()
        db.endTransaction()
      }
      "ROLLBACK" -> db.endTransaction()
      "PRAGMA" -> pragma(sql)
      else -> db.execSQL(sql)
    }
  }

  private fun pragma(sql: String) {
    val body = sql.trim().removePrefix("PRAGMA").removePrefix("pragma").trim().lowercase()
    when {
      body.startsWith("journal_mode") -> db.enableWriteAheadLogging()
      body.startsWith("busy_timeout") -> Unit // Android's connection sets its own busy handler
      else -> db.rawQuery(sql, null).use { it.moveToFirst() }
    }
  }

  override fun prepare(sql: String): SqlStatement = AndroidSqlStatement(this, db, sql)

  override fun changes(): Int = lastChanges

  override fun lastInsertRowId(): Long = lastRowId

  internal fun record(changes: Int, rowId: Long?) {
    lastChanges = changes
    if (rowId != null && rowId >= 0) lastRowId = rowId
  }

  override fun close() {
    if (db.isOpen) db.close()
  }

  companion object {
    /** Opens (creating when missing) the database file at [path]. */
    fun open(path: String): AndroidSqlConnection =
      AndroidSqlConnection(SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY))
  }
}

private class AndroidSqlStatement(
  private val owner: AndroidSqlConnection,
  private val db: SQLiteDatabase,
  private val sql: String,
) : SqlStatement {
  private val kind = keywordOf(sql)
  private val isQuery = kind == "SELECT" || kind == "WITH" || kind == "PRAGMA" || kind == "EXPLAIN" || kind == "VALUES"
  private val params = HashMap<Int, Any?>()
  private var cursor: Cursor? = null
  private var write: SQLiteStatement? = if (isQuery) null else db.compileStatement(sql)
  private var executed = false

  override fun bind(index: Int, value: Any?) {
    params[index] = value
  }

  override fun clearBindings() = params.clear()

  override fun reset() {
    cursor?.close()
    cursor = null
    executed = false
  }

  private fun ensureCursor(): Cursor {
    cursor?.let { return it }
    // Typed binds (null, integer, float, text, blob): rawQuery's own String[] arguments cannot carry a null.
    val factory = SQLiteDatabase.CursorFactory { _, driver, editTable, query ->
      for ((index, value) in params) {
        when (value) {
          null -> query.bindNull(index)
          is Long -> query.bindLong(index, value)
          is Int -> query.bindLong(index, value.toLong())
          is Short, is Byte -> query.bindLong(index, (value as Number).toLong())
          is Boolean -> query.bindLong(index, if (value) 1L else 0L)
          is Double -> query.bindDouble(index, value)
          is Float -> query.bindDouble(index, value.toDouble())
          is ByteArray -> query.bindBlob(index, value)
          else -> query.bindString(index, value.toString())
        }
      }
      SQLiteCursor(driver, editTable, query)
    }
    return db.rawQueryWithFactory(factory, sql, null, "").also { cursor = it }
  }

  override fun columnCount(): Int = if (isQuery) ensureCursor().columnCount else 0

  override fun step(): Boolean {
    if (isQuery) return ensureCursor().moveToNext()
    if (executed) return false
    val st = checkNotNull(write) { "statement closed" }
    st.clearBindings()
    for ((index, value) in params) {
      when (value) {
        null -> st.bindNull(index)
        is Long -> st.bindLong(index, value)
        is Int -> st.bindLong(index, value.toLong())
        is Short, is Byte -> st.bindLong(index, (value as Number).toLong())
        is Boolean -> st.bindLong(index, if (value) 1L else 0L)
        is Double -> st.bindDouble(index, value)
        is Float -> st.bindDouble(index, value.toDouble())
        is ByteArray -> st.bindBlob(index, value)
        else -> st.bindString(index, value.toString())
      }
    }
    when (kind) {
      "INSERT", "REPLACE" -> {
        val id = st.executeInsert()
        owner.record(if (id >= 0) 1 else 0, id)
      }
      "UPDATE", "DELETE" -> owner.record(st.executeUpdateDelete(), null)
      else -> {
        st.execute()
        owner.record(0, null)
      }
    }
    executed = true
    return false
  }

  override fun row(): List<Any?> {
    val c = checkNotNull(cursor) { "no current row" }
    return List(c.columnCount) { i ->
      when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> c.getString(i)
        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
        else -> null
      }
    }
  }

  override fun close() {
    cursor?.close()
    cursor = null
    write?.close()
    write = null
  }

  override fun release() = close()
}

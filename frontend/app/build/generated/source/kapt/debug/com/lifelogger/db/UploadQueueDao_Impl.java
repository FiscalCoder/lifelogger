package com.lifelogger.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UploadQueueDao_Impl implements UploadQueueDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<UploadQueueEntity> __insertionAdapterOfUploadQueueEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateAttempts;

  private final SharedSQLiteStatement __preparedStmtOfResetFailed;

  public UploadQueueDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfUploadQueueEntity = new EntityInsertionAdapter<UploadQueueEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `upload_queue` (`id`,`filePath`,`recordedAt`,`durationSeconds`,`attempts`,`status`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UploadQueueEntity entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getFilePath() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getFilePath());
        }
        if (entity.getRecordedAt() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getRecordedAt());
        }
        statement.bindDouble(4, entity.getDurationSeconds());
        statement.bindLong(5, entity.getAttempts());
        if (entity.getStatus() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getStatus());
        }
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE upload_queue SET status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateAttempts = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE upload_queue SET attempts = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfResetFailed = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE upload_queue SET status = 'pending', attempts = 0 WHERE status = 'failed'";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final UploadQueueEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfUploadQueueEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final String id, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        if (status == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, status);
        }
        _argIndex = 2;
        if (id == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, id);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAttempts(final String id, final int attempts,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateAttempts.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, attempts);
        _argIndex = 2;
        if (id == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, id);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateAttempts.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object resetFailed(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfResetFailed.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfResetFailed.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getPending(final Continuation<? super List<UploadQueueEntity>> $completion) {
    final String _sql = "SELECT * FROM upload_queue WHERE status = 'pending' AND attempts < 3 ORDER BY recordedAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<UploadQueueEntity>>() {
      @Override
      @NonNull
      public List<UploadQueueEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfRecordedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "recordedAt");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<UploadQueueEntity> _result = new ArrayList<UploadQueueEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UploadQueueEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpFilePath;
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _tmpFilePath = null;
            } else {
              _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            final String _tmpRecordedAt;
            if (_cursor.isNull(_cursorIndexOfRecordedAt)) {
              _tmpRecordedAt = null;
            } else {
              _tmpRecordedAt = _cursor.getString(_cursorIndexOfRecordedAt);
            }
            final float _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getFloat(_cursorIndexOfDurationSeconds);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpStatus;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmpStatus = null;
            } else {
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            }
            _item = new UploadQueueEntity(_tmpId,_tmpFilePath,_tmpRecordedAt,_tmpDurationSeconds,_tmpAttempts,_tmpStatus);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getFailed(final Continuation<? super List<UploadQueueEntity>> $completion) {
    final String _sql = "SELECT * FROM upload_queue WHERE status = 'failed' ORDER BY recordedAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<UploadQueueEntity>>() {
      @Override
      @NonNull
      public List<UploadQueueEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfRecordedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "recordedAt");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<UploadQueueEntity> _result = new ArrayList<UploadQueueEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UploadQueueEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpFilePath;
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _tmpFilePath = null;
            } else {
              _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            final String _tmpRecordedAt;
            if (_cursor.isNull(_cursorIndexOfRecordedAt)) {
              _tmpRecordedAt = null;
            } else {
              _tmpRecordedAt = _cursor.getString(_cursorIndexOfRecordedAt);
            }
            final float _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getFloat(_cursorIndexOfDurationSeconds);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpStatus;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmpStatus = null;
            } else {
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            }
            _item = new UploadQueueEntity(_tmpId,_tmpFilePath,_tmpRecordedAt,_tmpDurationSeconds,_tmpAttempts,_tmpStatus);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCountByStatus(final String status,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM upload_queue WHERE status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (status == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, status);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLastUploadTime(final Continuation<? super String> $completion) {
    final String _sql = "SELECT recordedAt FROM upload_queue WHERE status = 'uploaded' ORDER BY recordedAt DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<String>() {
      @Override
      @Nullable
      public String call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final String _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getString(0);
            }
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLocal(final Continuation<? super List<UploadQueueEntity>> $completion) {
    final String _sql = "SELECT * FROM upload_queue WHERE status != 'uploaded' ORDER BY recordedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<UploadQueueEntity>>() {
      @Override
      @NonNull
      public List<UploadQueueEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfRecordedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "recordedAt");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<UploadQueueEntity> _result = new ArrayList<UploadQueueEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UploadQueueEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpFilePath;
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _tmpFilePath = null;
            } else {
              _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            final String _tmpRecordedAt;
            if (_cursor.isNull(_cursorIndexOfRecordedAt)) {
              _tmpRecordedAt = null;
            } else {
              _tmpRecordedAt = _cursor.getString(_cursorIndexOfRecordedAt);
            }
            final float _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getFloat(_cursorIndexOfDurationSeconds);
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpStatus;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmpStatus = null;
            } else {
              _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            }
            _item = new UploadQueueEntity(_tmpId,_tmpFilePath,_tmpRecordedAt,_tmpDurationSeconds,_tmpAttempts,_tmpStatus);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}

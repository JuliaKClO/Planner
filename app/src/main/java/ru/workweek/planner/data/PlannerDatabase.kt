package ru.workweek.planner.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

class PlannerConverters {
    @TypeConverter fun dateToDay(value: LocalDate): Long = value.toEpochDay()
    @TypeConverter fun dayToDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)
    @TypeConverter fun timeToMinute(value: LocalTime?): Int? = value?.let { it.hour * 60 + it.minute }
    @TypeConverter fun minuteToTime(value: Int?): LocalTime? = value?.let { LocalTime.of(it / 60, it % 60) }
    @TypeConverter fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): Entry?

    @Upsert suspend fun upsert(entry: Entry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE entries SET completed = :completed, updatedAtUtc = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: String, completed: Boolean, updatedAt: Instant)

    @Query("SELECT * FROM entries WHERE plannedDate = :date ORDER BY completed, localTime IS NULL, localTime, createdAtUtc, id")
    fun observeDay(date: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE plannedDate BETWEEN :start AND :end ORDER BY plannedDate, completed, localTime IS NULL, localTime, createdAtUtc, id")
    fun observeWeekEntries(start: LocalDate, end: LocalDate): Flow<List<Entry>>

    @Query("SELECT plannedDate, count(*) AS count FROM entries WHERE plannedDate BETWEEN :start AND :end GROUP BY plannedDate")
    fun observeCounts(start: LocalDate, end: LocalDate): Flow<List<DayCount>>

    @Query("SELECT plannedDate, count(*) AS count FROM entries WHERE plannedDate BETWEEN :start AND :end GROUP BY plannedDate")
    suspend fun counts(start: LocalDate, end: LocalDate): List<DayCount>

    @Query("SELECT * FROM entries WHERE plannedDate = :date ORDER BY completed, localTime IS NULL, localTime, createdAtUtc, id LIMIT 1")
    suspend fun first(date: LocalDate): Entry?

    @Query("SELECT DISTINCT plannedDate FROM entries WHERE plannedDate BETWEEN :start AND :end ORDER BY plannedDate")
    fun observeMarkedDates(start: LocalDate, end: LocalDate): Flow<List<LocalDate>>

    @Query("SELECT * FROM entries WHERE plannedDate = :date AND completed = 0 AND remindOnPreviousDay = 1 ORDER BY localTime IS NULL, localTime, createdAtUtc, id")
    suspend fun digest(date: LocalDate): List<Entry>

    @RawQuery(observedEntities = [Entry::class])
    fun observeSearch(query: SupportSQLiteQuery): Flow<List<Entry>>
}

@androidx.room.Entity(tableName = "digest_deliveries")
data class DigestDelivery(
    @androidx.room.PrimaryKey val sendLocalDate: LocalDate,
    val targetLocalDate: LocalDate,
    val notificationKey: String,
    val deliveryState: String,
    val publishedAtUtc: Instant? = null,
)

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM digest_deliveries WHERE sendLocalDate = :date")
    suspend fun get(date: LocalDate): DigestDelivery?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(delivery: DigestDelivery)
    @Query("SELECT * FROM digest_deliveries ORDER BY sendLocalDate")
    suspend fun all(): List<DigestDelivery>
}

@Database(entities = [Entry::class, DigestDelivery::class], version = 1, exportSchema = true)
@TypeConverters(PlannerConverters::class)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun deliveries(): DeliveryDao

    companion object {
        @Volatile private var instance: PlannerDatabase? = null

        fun open(context: Context): PlannerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, PlannerDatabase::class.java, "workweek.db"
            ).build().also { instance = it }
        }
    }
}

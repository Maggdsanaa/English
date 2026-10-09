package app.ocrpdf.arabic.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

object JobStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"
}

object JobStage {
    const val OCR = "OCR"
    const val BUILD = "BUILD"
    const val COMPRESS = "COMPRESS"
    const val FINISH = "FINISH"
}

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pageCount: Int,
    val languages: String,        // "ara", "eng", "ara+eng"
    val compression: Int,         // 0 off, 1 balanced, 2 strong
    val ocrAll: Boolean,
    val dpi: Int,
    val status: String,
    val stage: String,
    val processedPages: Int,
    val ocrPages: Int,
    val nativePages: Int,
    val error: String?,           // "PASSWORD" | "INVALID" | "OTHER:<message>"
    val createdAt: Long,
    val finishedAt: Long?,
    val inputBytes: Long,
    val outputBytes: Long,
)

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE jobs SET processedPages=:p, ocrPages=:o, nativePages=:n, stage=:stage WHERE id=:id")
    suspend fun progress(id: String, p: Int, o: Int, n: Int, stage: String)

    @Query("UPDATE jobs SET status=:status, stage=:stage, error=:error WHERE id=:id")
    suspend fun setStatus(id: String, status: String, stage: String, error: String?)

    @Query("UPDATE jobs SET status='DONE', stage='FINISH', error=NULL, finishedAt=:at, outputBytes=:out WHERE id=:id")
    suspend fun markDone(id: String, at: Long, out: Long)
}

@Database(entities = [JobEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "jobs.db").build().also { inst = it }
        }
    }
}

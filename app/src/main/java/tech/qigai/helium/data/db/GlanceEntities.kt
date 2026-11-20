package tech.qigai.helium.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A source the user added to 速览: an RSS/Atom feed, a plain web page, or an X account through a bridge. */
@Entity(tableName = "feed_sources")
data class FeedSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** RSS · WEB · X */
    val kind: String,
    /** What the user typed (URL, @handle). */
    val input: String,
    /** The URL actually fetched (feed URL after autodiscovery, bridge URL for X). */
    val url: String,
    val title: String,
    val siteUrl: String?,
    val enabled: Boolean,
    val addedAt: Long,
    val lastFetchedAt: Long?,
    val lastError: String?,
)

@Entity(
    tableName = "feed_items",
    indices = [Index(value = ["sourceId", "guid"], unique = true), Index("publishedAt")],
)
data class FeedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val guid: String,
    val title: String,
    val link: String,
    val summary: String,
    /** Feed date, or first-seen time when the feed has none. */
    val publishedAt: Long,
    val fetchedAt: Long,
    /** Cached "为什么推给你" line; null until the model wrote one. */
    val reason: String?,
)

/** A generated custom card: [definition] is CardDefinition JSON, [valuesJson] the last extracted lines JSON. */
@Entity(tableName = "glance_cards")
data class GlanceCardEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val definition: String,
    val intervalMinutes: Int,
    val size: String,
    val valuesJson: String?,
    val lastRefreshAt: Long?,
    val nextRefreshAt: Long,
    val lastError: String?,
    val position: Int,
    val createdAt: Long,
)

/** Single-row cache (id = 0) of the last Open-Meteo forecast for the resolved place. */
@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Int = 0,
    val place: String,
    val lat: Double,
    val lon: Double,
    /** "gps" · "manual" · "default" */
    val placeSource: String,
    val json: String,
    val fetchedAt: Long,
)

data class FeedItemRow(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Long,
    val reason: String?,
    val sourceTitle: String,
    val sourceKind: String,
)

@Dao
interface GlanceDao {
    @Query("SELECT * FROM feed_sources ORDER BY addedAt")
    fun observeSources(): Flow<List<FeedSourceEntity>>

    @Query("SELECT * FROM feed_sources WHERE enabled = 1 ORDER BY addedAt")
    suspend fun enabledSources(): List<FeedSourceEntity>

    @Query("SELECT * FROM feed_sources WHERE url = :url LIMIT 1")
    suspend fun sourceByUrl(url: String): FeedSourceEntity?

    @Insert
    suspend fun insertSource(s: FeedSourceEntity): Long

    @Query("UPDATE feed_sources SET lastFetchedAt = :at, lastError = :error, title = CASE WHEN :title = '' THEN title ELSE :title END WHERE id = :id")
    suspend fun markFetched(id: Long, at: Long, error: String?, title: String)

    @Query("DELETE FROM feed_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("DELETE FROM feed_items WHERE sourceId = :sourceId")
    suspend fun deleteItemsOf(sourceId: Long)

    /** Existing (sourceId, guid) pairs are kept untouched so a cached reason survives refreshes. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<FeedItemEntity>): List<Long>

    @Query(
        """SELECT i.id, i.sourceId, i.title, i.link, i.summary, i.publishedAt, i.reason, s.title AS sourceTitle, s.kind AS sourceKind
           FROM feed_items i JOIN feed_sources s ON s.id = i.sourceId
           WHERE s.enabled = 1 ORDER BY i.publishedAt DESC LIMIT :limit""",
    )
    fun observeItems(limit: Int): Flow<List<FeedItemRow>>

    @Query(
        """SELECT i.id, i.sourceId, i.title, i.link, i.summary, i.publishedAt, i.reason, s.title AS sourceTitle, s.kind AS sourceKind
           FROM feed_items i JOIN feed_sources s ON s.id = i.sourceId
           WHERE i.reason IS NULL AND s.enabled = 1 ORDER BY i.publishedAt DESC LIMIT :limit""",
    )
    suspend fun itemsWithoutReason(limit: Int): List<FeedItemRow>

    @Query("UPDATE feed_items SET reason = :reason WHERE id = :id")
    suspend fun setReason(id: Long, reason: String)

    /** Keep the newest [keep] items per source. */
    @Query(
        """DELETE FROM feed_items WHERE sourceId = :sourceId AND id NOT IN
           (SELECT id FROM feed_items WHERE sourceId = :sourceId ORDER BY publishedAt DESC LIMIT :keep)""",
    )
    suspend fun trim(sourceId: Long, keep: Int)

    @Query("SELECT * FROM glance_cards ORDER BY position, createdAt")
    fun observeCards(): Flow<List<GlanceCardEntity>>

    @Query("SELECT * FROM glance_cards ORDER BY position, createdAt")
    suspend fun cards(): List<GlanceCardEntity>

    @Upsert
    suspend fun upsertCard(c: GlanceCardEntity)

    @Query("DELETE FROM glance_cards WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("SELECT * FROM weather_cache WHERE id = 0")
    fun observeWeather(): Flow<WeatherCacheEntity?>

    @Query("SELECT * FROM weather_cache WHERE id = 0")
    suspend fun weather(): WeatherCacheEntity?

    @Upsert
    suspend fun putWeather(w: WeatherCacheEntity)
}

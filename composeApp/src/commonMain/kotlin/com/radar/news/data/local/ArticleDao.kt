package com.radar.news.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    /** The timeline: newest first, paged 20 at a time by Paging 3. */
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC, id DESC")
    fun pagingSource(): PagingSource<Int, ArticleEntity>

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<ArticleEntity>>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeCount(): Flow<Int>

    /**
     * How many stories are newer than what the user has already seen at the top of the list.
     * Drives the "{n} أخبار جديدة" pill.
     */
    @Query("SELECT COUNT(*) FROM articles WHERE publishedAt > :watermark")
    fun observeCountNewerThan(watermark: Long): Flow<Int>

    @Query("SELECT MAX(publishedAt) FROM articles")
    suspend fun newestPublishedAt(): Long?

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun byId(id: String): ArticleEntity?

    /**
     * Dedupe candidates: everything in the buckets touching the 12-hour window around a
     * new item. Indexed on `dayBucket`, so this stays a small bounded read no matter how
     * large the table gets — never a full scan.
     */
    @Query("SELECT * FROM articles WHERE dayBucket BETWEEN :fromBucket AND :toBucket")
    suspend fun inBuckets(fromBucket: Long, toBucket: Long): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE canonicalUrl IN (:urls)")
    suspend fun byCanonicalUrls(urls: List<String>): List<ArticleEntity>

    /**
     * IGNORE rather than REPLACE: a re-fetched article must not overwrite the row that
     * already accumulated `duplicateSourceIds` and `notified`. Returns -1 per skipped row,
     * which is how the sync tells genuinely new stories from ones it has seen.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    @Update
    suspend fun update(article: ArticleEntity)

    @Update
    suspend fun updateAll(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET notified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>)

    /**
     * Articles worth a push, newest first — see SyncConfig.NOTIFY_SCORE_THRESHOLD.
     *
     * `seenInFeed = 0` is what keeps the worker from announcing a story the foreground already
     * put on screen. S2 stops a foreground sync from *publishing*; without this clause it did
     * nothing about *eligibility*, so S3's re-read picked those same rows up on the next
     * background run and buzzed about an article the user was looking at. See DECISIONS.md S5.
     */
    @Query(
        """
        SELECT * FROM articles
        WHERE notified = 0 AND seenInFeed = 0
          AND breakingScore >= :minScore AND publishedAt >= :since
        ORDER BY publishedAt DESC
        """,
    )
    suspend fun pendingNotifications(minScore: Int, since: Long): List<ArticleEntity>

    /** 48-hour retention, per the spec. */
    @Query("DELETE FROM articles WHERE publishedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int

    @Query("DELETE FROM articles")
    suspend fun clear()

    @Transaction
    suspend fun insertAndReturnNew(articles: List<ArticleEntity>): List<ArticleEntity> {
        val rowIds = insertAll(articles)
        return articles.filterIndexed { index, _ -> rowIds.getOrElse(index) { -1L } != -1L }
    }
}

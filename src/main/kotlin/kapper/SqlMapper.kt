package kapper

import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

object SqlMapper {

    private const val COLLECT_PER_ITEMS = 1000
    private const val COLLECT_HIT_COUNT_MIN = 0
    private val collect = AtomicInteger(0)

    private val queryCache: ConcurrentHashMap<Identity, CacheInfo> = ConcurrentHashMap()

    private fun getColumnHash(reader: DataReader, startBound: Int = 0, length: Int = -1): Int {
        val max = if(length < 0) reader.metaData.columnCount else startBound + length
        var hash = (-37 * startBound) + max

        for(i in startBound until max) {
            val columnIndex = i + 1
            val tmp = reader.metaData.getColumnName(columnIndex)
            hash = (-79 * ((hash * 31) + (tmp?.hashCode() ?: 0))) + (reader.metaData.getColumnClassName(columnIndex)?.hashCode() ?: 0)
        }

        return hash
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> execute(
        connection: Connection,
        dbCommand: DbCommand,
        targetClass: KClass<*>
    ): Iterable<T> {

        val identity = Identity(dbCommand.commandText, dbCommand.commandType, connection, targetClass)

        return dbCommand.executeReader().use {
            val cacheInfo = getCacheInfo(identity, true) {
                CacheInfo(Deserializer.create(identity.hashCode(), targetClass, it))
            }

            val result = mutableListOf<T>()

            while (it.read()) {
                result.add(cacheInfo.deserializer.deserialize(it) as T)
            }

            result
        }
    }

    private fun getCacheInfo(identity: Identity, addToCache: Boolean, factory: () -> CacheInfo): CacheInfo {
        val cached = tryGetQueryCache(identity)

        if(cached.first) {
            return cached.second!!
        }

        val cacheInfo = factory()

        if(addToCache)
            setQueryCache(identity, cacheInfo)

        return cacheInfo
    }

    private fun collectCacheGarbage() {
        try {
            for(entry in queryCache) {
                if(entry.value.getHitCount() <= COLLECT_HIT_COUNT_MIN) {
                    queryCache.remove(entry.key)
                }
            }
        }
        finally {
            collect.set(0)
        }
    }

    private fun tryGetQueryCache(key: Identity): Pair<Boolean, CacheInfo?> {
        val cacheInfo = queryCache[key]
        val found = cacheInfo != null

        if(found) {
            cacheInfo!!.recordHit()
        }

        return Pair(found, cacheInfo)
    }

    private fun setQueryCache(key: Identity, cacheInfo: CacheInfo) {
        if(collect.incrementAndGet() == COLLECT_PER_ITEMS) {
            collectCacheGarbage()
        }

        queryCache[key] = cacheInfo
    }

    fun purgeQueryCache() {
        queryCache.clear()
    }
}

internal class CacheInfo(var deserializer: Deserializer) {

    private var hitCount: AtomicInteger = AtomicInteger(0)

    fun getHitCount() = hitCount.get()

    fun recordHit() {
        hitCount.incrementAndGet()
    }
}
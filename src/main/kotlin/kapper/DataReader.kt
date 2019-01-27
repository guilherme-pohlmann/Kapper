package kapper

import java.sql.ResultSet

class DataReader(internal val resultSet: ResultSet): AutoCloseable {
    //Some drivers returns a new object every call, its a good idea to cache it, for performance
    internal val metaData by lazy {
        resultSet.metaData ?: throw NoMetaDataException()
    }

    val columns by lazy {
        IntRange(1, metaData.columnCount).map {
            metaData.getColumnName(it) ?: ""
        }.sortedWith(ColumnNameComparator)
    }

    override fun close() {
        resultSet.close()
    }

    fun read() = resultSet.next()
}

class NoMetaDataException: Exception("There was no metaData available.")

object ColumnNameComparator: Comparator<String> {
    override fun compare(p0: String?, p1: String?): Int {
        if (p0 == null) {
            if (p1 == null) {
                return 0
            }
            return -1
        }
        if (p1 == null) {
            return 1
        }

        return p0.compareTo(p1, true)
    }
}

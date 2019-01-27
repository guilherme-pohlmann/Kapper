package kapper

import java.sql.Connection
import kotlin.reflect.KClass

class Identity(private val sql: String,
               private val commandType: CommandType,
               private val connectionString: String,
               private val type: KClass<*>?) {

    private val hashCode: Int

    constructor(sql: String,
                commandType: CommandType,
                connection: Connection,
                type: KClass<*>):
            this(sql, commandType, connection.metaData.url, type)

    init {
        var tmpHash = 17
        tmpHash = (tmpHash * 23) + commandType.hashCode()
        tmpHash = (tmpHash * 23) + sql.hashCode()
        tmpHash = (tmpHash * 23) + (type?.hashCode() ?: 0)
        tmpHash = (tmpHash * 23) + connectionString.hashCode()

        hashCode = tmpHash
    }

    override fun hashCode() = hashCode

    override fun equals(other: Any?): Boolean {
        val obj = other as Identity? ?: return false

        return obj.type == type &&
               obj.sql == sql &&
               obj.commandType == commandType &&
               obj.connectionString.equals(connectionString, true)
    }
}
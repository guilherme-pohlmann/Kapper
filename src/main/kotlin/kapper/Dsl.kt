package kapper

import java.math.BigDecimal
import java.sql.Connection
import java.sql.Time
import java.sql.Timestamp
import java.util.*

class KapperDsl {

    private var commandText: String? = null
    private var commandType: CommandType = CommandType.Text
    private var timeout: Int? = null
    private var autoCommit: Boolean = true
    private var parameters: List<DbParameter>? = null

    fun query(query: () -> String) {
        commandText = query()
        commandType = CommandType.Text
    }

    fun call(name: () -> String) {
        commandText = name()
        commandType = CommandType.StoredProcedure
    }

    fun timeout(value: () -> Int) {
        timeout = value()
    }

    fun noAutoCommit() {
        autoCommit = false
    }

    infix fun params(parameters: () -> List<DbParameter>) {
        this.parameters = parameters()
    }

    fun buildCommand(connection: Connection): DbCommand {
        return DbCommand(
            connection,
            commandText!!,
            commandType,
            timeout,
            parameters ?: emptyList()
        )
    }
}

fun named(init: NamedParameterBuilder.() -> Unit): List<DbParameter> {
    return NamedParameterBuilder().apply(init).parameters
}

class NamedParameterBuilder: ParameterBuilder() {
    infix fun String.`=`(value: Boolean) {
        add(this, DbType.BIT, value)
    }

    infix fun String.`=`(value: Byte) {
        add(this, DbType.TINYINT, value)
    }

    infix fun String.`=`(value: Long) {
        add(this, DbType.BIGINT, value)
    }

    infix fun String.`=`(value: Char) {
        add(this, DbType.CHAR, value)
    }

    infix fun String.`=`(value: BigDecimal) {
        add(this, DbType.DECIMAL, value)
    }

    infix fun String.`=`(value: Int) {
        add(this, DbType.INTEGER, value)
    }

    infix fun String.`=`(value: Short) {
        add(this, DbType.SMALLINT, value)
    }

    infix fun String.`=`(value: Float) {
        add(this, DbType.FLOAT, value)
    }

    infix fun String.`=`(value: Double) {
        add(this, DbType.DOUBLE, value)
    }

    infix fun String.`=`(value: String) {
        add(this, DbType.VARCHAR, value)
    }

    infix fun String.`=`(value: Date) {
        add(this, DbType.DATE, value)
    }

    infix fun String.`=`(value: Time) {
        add(this, DbType.TIME, value)
    }

    infix fun String.`=`(value: Timestamp) {
        add(this, DbType.TIMESTAMP, value)
    }
}

abstract class ParameterBuilder {

    internal val parameters: MutableList<DbParameter> = mutableListOf()
    private var index = 1

    protected fun add(name: String, dbType: DbType, value: Any?) {
        parameters.add(NamedDbParameter(name, index, dbType, value))
        index++
    }
}

inline fun <reified T> Connection.execute(init: KapperDsl.() -> Unit): Iterable<T> {
    val command = KapperDsl().apply(init).buildCommand(this)
    return SqlMapper.execute(this, command, T::class)
}
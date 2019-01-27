package kapper

import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement


class DbCommand(
    private val connection: Connection,
    val commandText: String,
    val commandType: CommandType,
    val commandTimeout: Int? = null,
    val parameters: List<DbParameter> = emptyList()
) {

    private fun buildStatement(): PreparedStatement {
        val statement = if(commandType == CommandType.Text) connection.prepareStatement(commandText) else connection.prepareCall(commandText)

        if(commandTimeout != null) {
            statement.queryTimeout = commandTimeout
        }

        parameters.forEach {
            it.set(statement)
        }

        return statement
    }

    fun executeReader(): DataReader {
        return DataReader(buildStatement().executeQuery())
    }
}

enum class CommandType {
    Text,
    StoredProcedure
}

abstract class DbParameter(val type: DbType, val value: Any?) {
    abstract fun set(statement: PreparedStatement)
}

open class IndexedDbParameter(private val index: Int, type: kapper.DbType, value: Any?): DbParameter(type, value) {
    override fun set(statement: PreparedStatement) {
        statement.setObject(index, value, type.value)
    }
}

class NamedDbParameter(private val name: String, index: Int, type: kapper.DbType, value: Any?): IndexedDbParameter(index,type, value) {
    override fun set(statement: PreparedStatement) {
        if(statement is CallableStatement) {
            statement.setObject(name, value, type.value)
        }
        else {
            super.set(statement)
        }
    }
}

enum class DbType(val value: Int) {
    BIT(-7),
    TINYINT(-6),
    BIGINT(-5),
    LONGVARBINARY(-4),
    VARBINARY(-3),
    BINARY(-2),
    LONGVARCHAR(-1),
    NULL(0),
    CHAR(1),
    NUMERIC(2),
    DECIMAL(3),
    INTEGER(4),
    SMALLINT(5),
    FLOAT(6),
    REAL(7),
    DOUBLE(8),
    VARCHAR(12),
    DATE(91),
    TIME(92),
    TIMESTAMP(93),
    OTHER(1111);

    companion object {
        fun fromInt(value: Int) = DbType.values().firstOrNull { it.value == value } ?: DbType.OTHER
    }
}
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

    val innerStatement by lazy(::buildStatement)

    private fun buildStatement(): PreparedStatement {
        val statement = createStatement()

        if(commandTimeout != null) {
            statement.queryTimeout = commandTimeout
        }

        if(statement is CallableStatement) {
            parameters.forEach {
                statement.setObject(it.name, it.value, it.type.value)
            }
        }
        else {
            parameters.forEach {
                statement.setObject(it.index, it.value, it.type.value)
            }
        }

        return statement
    }

    private fun createStatement(): PreparedStatement {
        return if(commandType == CommandType.Text)
            connection.prepareStatement(commandText)
        else
            connection.prepareCall(commandText)
    }

    fun executeReader(): DataReader {
        return DataReader(buildStatement().executeQuery())
    }
}

enum class CommandType {
    Text,
    StoredProcedure
}

class DbParameter(val name: String, val index: Int, val type: DbType, val value: Any?)

enum class DbType(val value: Int) {
    BIT(-7),
    TINYINT(-6),
    BIGINT(-5),
    //LONGVARBINARY(-4),
    //VARBINARY(-3),
    //BINARY(-2),
    //LONGVARCHAR(-1),
    //NULL(0),
    CHAR(1),
    //NUMERIC(2),
    DECIMAL(3),
    INTEGER(4),
    SMALLINT(5),
    FLOAT(6),
    //REAL(7),
    DOUBLE(8),
    VARCHAR(12),
    DATE(91),
    TIME(92),
    TIMESTAMP(93),
    OTHER(1111);
}
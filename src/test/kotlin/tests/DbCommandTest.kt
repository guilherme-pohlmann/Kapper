package tests

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kapper.CommandType
import kapper.DbCommand
import kapper.DbParameter
import kapper.DbType
import org.junit.Test
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement

class DbCommandTest {

    @Suppress("UsePropertyAccessSyntax")
    @Test
    fun TimeoutPropertyIsBeingSet() {
        val st = mock<PreparedStatement>()

        val conn = mock<Connection> {
            on { prepareStatement(any()) } doReturn st
        }

        val timeout = 749
        val command = DbCommand(conn,"SELECT * FROM TABLE", CommandType.Text, 749)
        command.innerStatement.run {
            verify(st).setQueryTimeout(timeout)
        }
    }

    @Test
    fun ParameterShouldBeSetByNameInCallableStatement() {
        val st = mock<CallableStatement>()

        val conn = mock<Connection> {
            on { prepareCall(any()) } doReturn st
        }

        val params = listOf(
            DbParameter("p1", 1, DbType.INTEGER, 10),
            DbParameter("p2", 2, DbType.VARCHAR, "BLA")
        )

        val command = DbCommand(conn,"  SELECT * FROM TABLE", CommandType.StoredProcedure, parameters = params)
        command.innerStatement.run {
            verify(conn) {
                1.times {
                    prepareCall(command.commandText)
                }

                0.times {
                    prepareStatement(command.commandText)
                }

                params.forEach {
                    verify(st).setObject(it.name, it.value, it.type.value)
                }
            }
        }
    }

    @Test
    fun ParameterShouldBeSetByIndexInPreparedStatement() {
        val st = mock<PreparedStatement>()

        val conn = mock<Connection> {
            on { prepareStatement(any()) } doReturn st
        }

        val params = listOf(
            DbParameter("p1", 1, DbType.INTEGER, 10),
            DbParameter("p2", 2, DbType.VARCHAR, "BLA")
        )

        val command = DbCommand(conn,"  SELECT * FROM TABLE", CommandType.Text, parameters = params)
        command.innerStatement.run {
            verify(conn) {
                0.times {
                    prepareCall(command.commandText)
                }

                1.times {
                    prepareStatement(command.commandText)
                }

                params.forEach {
                    verify(st).setObject(it.index, it.value, it.type.value)
                }
            }
        }
    }
}
//package tests
//
//import kapper.execute
//import kapper.named
//import org.junit.Test
//import java.sql.DriverManager
//
//class Pessoa(val nome: String = "", val idade: Int = 0) {
//
//    //constructor() : this("", 0, "")
//
//}
//
//class SqlMapperTest {
//  @Test
//  fun foo() {
//      val url = "jdbc:mysql://localhost/teste"
//      val con = DriverManager.getConnection(url, "root", "123456")
//
//      val list = con.execute<Pessoa> {
//          query { "select * from pessoa where idade > ?" }
//          params {
//              named {
//                  "idade" `=` 8
//              }
//          }
//      }
//
//      list.forEach {
//          println(it.nome)
//      }
//
//      val list2 = con.execute<Pessoa> {
//          query { "select * from pessoa where idade > ?" }
//          params {
//              named {
//                  "idade" `=` 8
//              }
//          }
//      }
//
//      list2.forEach {
//          println(it.nome)
//      }
//
//      val list3 = con.execute<Pessoa> {
//          query { "select * from pessoa" }
//      }
//
//      list3.forEach {
//          println(it.nome)
//      }
//  }
//}
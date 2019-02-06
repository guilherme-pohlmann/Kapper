# Kapper

Kapper is extremely inspired by [Dapper](https://github.com/StackExchange/Dapper). 
You could say it is Dapper for Kotlin.

It's designed to be very fast and very lightweight, with little dependencies overhead to your application.

## How does it work

When you execute a `Query` through Kapper, it uses reflection just the first time, to scan your `ResultSet`
and your target object and generate JVM ByteCode at runtime using [ASM](https://asm.ow2.io/).

### Example

Consider the following Kotlin class

```kotlin

class Person(val name: String, val age: Int)

```

And the following `Kapper DSL` code

```kotlin

val list = con.execute<Person> {
   query { "SELECT Name, Age FROM Person WHERE Age > ?" }
   params {
      named {
          "Age" `=` 18
       }
   }
}

``` 

Internally, it generates a `Mapper Class` specific for this Query and return type (`Person`).
The decompiled Mapper Class bytecode will look like this:

```java

//The number is a hash genereted by Kapper for this map.
public class PersonMapper8791156489 {
    public static Person mapper(ResultSet result) throws SQLException {
        return new Person(result.getString("name"), result.getInt("age"));
    }
}

``` 

Things to note:

- The check for rows on the `ResultSet` is made beforehand, so whenever it calls the mapper, it has rows.
- This mapper is calling the constructor directly, because the class declared a constructor that matches all returned rows. If that was not the case, Kapper will look for a parameterless constructor, invoke it, and generate code to map property by property, calling its setters.
- The parameterless constructor doesn't have to be public, however, non public constructor may have a little performance impact.
- It also works if you provide a constructor with default values for all properties.

## Features

The current implementation provides the extension function `execute` to JDBC `Connection` class. 
The function takes a DSL that allow the user to provide the query and named parameters (as shown above).
Even though named parameters are allowed in the DSL, for now it uses JDBC positional parameters.


## The future

There is a lot of work to be add on Kapper. If you liked the projects idea, please contribute.
There is a board on Trello with the tasks for new features, if you want to join please let me know.





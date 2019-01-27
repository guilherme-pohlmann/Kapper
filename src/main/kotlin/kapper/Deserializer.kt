package kapper

import kapper.serialization.AsmMapper
import java.lang.reflect.Method
import java.sql.ResultSet
import kotlin.reflect.KClass

internal class Deserializer {

    private val hash: Int
    private val targetClass: KClass<*>
    private val mapperClass: Class<*>
    private val mapperMethod: Method

    private constructor(targetClass: KClass<*>, hash: Int, mapperClass: Class<*>) {
        this.targetClass = targetClass
        this.hash = hash
        this.mapperClass = mapperClass
        this.mapperMethod = mapperClass.getDeclaredMethod("mapper", ResultSet::class.java)
    }

    companion object {
        fun create(hash: Int, targetClass: KClass<*>, reader: DataReader): Deserializer {
            val mapperClass = AsmMapper.create(targetClass, reader, hash)
            return Deserializer(targetClass, hash, mapperClass!!)
        }
    }

    fun deserialize(reader: DataReader): Any? {
        return mapperMethod.invoke(mapperClass, reader.resultSet)
    }

    override fun equals(other: Any?): Boolean {
        if(other is Deserializer) {
            return other.hash == hash && other.targetClass == targetClass
        }
        return false
    }

    override fun hashCode(): Int {
        var hash = 17
        hash += hash * 31
        hash = hash * 31 + targetClass.hashCode()

        return hash
    }
}
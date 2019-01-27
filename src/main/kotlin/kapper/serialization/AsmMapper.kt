package kapper.serialization

import kapper.ColumnNameComparator
import kapper.DataReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import java.lang.invoke.CallSite
import java.lang.invoke.ConstantCallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType

internal object AsmMapper : ClassLoader(Thread.currentThread().contextClassLoader) {

    private val methodTable = mapOf(
        KTypeCache.KString to TableEntry(
            "getString",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)V"
        ),
        KTypeCache.KStringNullable to TableEntry(
            "getString",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)V"
        ),
        KTypeCache.KInt to TableEntry(
            "getInt",
            "(Ljava/lang/String;)I",
            "(I)V"),
        KTypeCache.KIntNullable to TableEntry(
            "getInt",
            "(Ljava/lang/String;)I",
            "(I)V",
             NullableEntry("java/lang/Integer" , "valueOf", "(I)Ljava/lang/Integer;")
        ),
        KTypeCache.KBoolean to TableEntry(
            "getBoolean",
            "(Ljava/lang/String;)Z",
            "(Z)V"
        ),
        KTypeCache.KBooleanNullable to TableEntry(
            "getBoolean",
            "(Ljava/lang/String;)Z",
            "(Ljava/lang/Boolean;)V",
            NullableEntry("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;")
        ),
        KTypeCache.KByte to TableEntry(
            "getByte",
            "(Ljava/lang/String;)B",
            "(B)V"
        ),
        KTypeCache.KByteNullable to TableEntry(
            "getByte",
            "(Ljava/lang/String;)B",
            "(B)V",
            NullableEntry("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;")
        ),
        KTypeCache.KDate to TableEntry(
            "getDate",
            "(Ljava/lang/String;)Ljava/sql/Date;",
            "(Ljava/util/Date;)V"
        ),
        KTypeCache.KDateNullable to TableEntry(
            "getDate",
            "(Ljava/lang/String;)Ljava/sql/Date;",
            "(Ljava/util/Date;)V"
        ),
        KTypeCache.KDouble to TableEntry(
            "getDouble",
            "(Ljava/lang/String;)D",
            "(D)V"
        ),
        KTypeCache.KDoubleNullable to TableEntry(
            "getDouble",
            "(Ljava/lang/String;)D",
            "(D)V",
            NullableEntry("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;")
        ),
        KTypeCache.KFloat to TableEntry(
            "getFloat",
            "(Ljava/lang/String;)F",
            "(F)V"
        ),
        KTypeCache.KFloatNullable to TableEntry(
            "getFloat",
            "(Ljava/lang/String;)F",
            "(F)V",
            NullableEntry("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;")
        ),
        KTypeCache.KLong to TableEntry(
            "getLong",
            "(Ljava/lang/String;)J",
            "(J)V"
        ),
        KTypeCache.KLongNullable to TableEntry(
            "getLong",
            "(Ljava/lang/String;)J",
            "(J)V",
            NullableEntry("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
        ),
        KTypeCache.KBigDecimal to TableEntry(
            "getBigDecimal",
            "(Ljava/lang/String;)Ljava/math/BigDecimal;",
            "(Ljava/math/BigDecimal;)V"
        ),
        KTypeCache.KBigDecimalNullable to TableEntry(
            "getBigDecimal",
            "(Ljava/lang/String;)Ljava/math/BigDecimal;",
            "(Ljava/math/BigDecimal;)V"
        ),
        KTypeCache.KShort to TableEntry(
            "getShort",
            "(Ljava/lang/String;)S",
            "(S)V"
        ),
        KTypeCache.KShortNullable to TableEntry(
            "getShort",
            "(Ljava/lang/String;)S",
            "(S)V",
            NullableEntry("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;")
        )
    )

    fun create(target: KClass<*>, reader: DataReader, hash: Int): Class<*>? {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val mv: MethodVisitor
        val className = "${target.simpleName}Mapper$hash"

        cw.visit(
            V1_7, ACC_PUBLIC + ACC_SUPER,
            className,
            null,
            "java/lang/Object",
            null
        )

        emmitConstructor(cw, target)

        mv = cw.visitMethod(
            ACC_PUBLIC + ACC_STATIC,
            "mapper",
            "(Ljava/sql/ResultSet;)${target.byteCodeName(true)}",
            null,
            arrayOf("java/sql/SQLException")
        )

        mv.visitCode()
        emmitMapperMethod(target, mv, reader)
        cw.visitEnd()

        val byteCode = cw.toByteArray()
        return super.defineClass(className, byteCode, 0, byteCode.size)
    }

    private fun emmitMapperMethod(target: KClass<*>,
                                  mv: MethodVisitor,
                                  reader: DataReader
    ) {
        val matchConstructor = findMatchConstructor(target, reader)

        if(matchConstructor != null) {
            emmitConstructorMapping(target, mv, matchConstructor)
        } else {
            val constructor = findParameterlessConstructor(target) ?: throw IllegalArgumentException("Please provide a parameterless constructor for $target")
            emmitPropertyMapping(target, mv, constructor, reader)
        }
    }

    private fun emmitConstructor(cw: ClassWriter, target: KClass<*>) {
        val mv = cw.visitMethod(
            ACC_PUBLIC,
            "<init>",
            "()V",
            null,
            null
        )
        mv.visitCode()

        val l0 = Label()
        mv.visitLabel(l0)
        mv.visitVarInsn(ALOAD, 0)
        mv.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false
        )

        val l1 = Label()
        mv.visitLabel(l1)
        mv.visitInsn(RETURN)

        val l2 = Label()
        mv.visitLabel(l2)

        mv.visitLocalVariable(
            "this",
            target.byteCodeName(true),
            null,
            l0,
            l2,
            0
        )

        mv.visitMaxs(1, 1)
        mv.visitEnd()
    }

    private fun emmitConstructorMapping(target: KClass<*>,
                                        mv: MethodVisitor,
                                        constructor: KFunction<*>) {
        val l0 = Label()
        val l1 = Label()
        val l2 = Label()
        val l3 = Label()

        mv.visitLabel(l0)

        mv.visitInsn(ACONST_NULL)
        mv.visitVarInsn(ASTORE, 1)

        mv.visitLabel(l1)

        mv.visitTypeInsn(NEW, target.byteCodeName())

        mv.visitInsn(DUP)

        constructor.parameters.forEach {
            mv.visitVarInsn(ALOAD, 0)
            mv.visitLdcInsn(it.name)

            val methodEntry = methodTable[it.type] ?: throw IllegalArgumentException("Unsupported property type ${it.type}")

            mv.visitMethodInsn(
                INVOKEINTERFACE,
                "java/sql/ResultSet",
                methodEntry.methodName,
                methodEntry.getMethodDesc,
                true)

            if(it.type.isMarkedNullable && methodEntry.nullableEntry != null) {
                mv.visitMethodInsn(
                    INVOKESTATIC,
                    methodEntry.nullableEntry.owner,
                    methodEntry.nullableEntry.methodName,
                    methodEntry.nullableEntry.methodDesc,
                    false
                )
            }
        }

        mv.visitMethodInsn(
            INVOKESPECIAL,
            target.byteCodeName(),
            "<init>",
            "(${constructor.parameters.joinToString(separator = "") { it.type.byteCodeName(true) }})V",
            false
        )

        mv.visitVarInsn(ASTORE, 1)

        mv.visitLabel(l2)
        mv.visitVarInsn(ALOAD, 1)
        mv.visitInsn(ARETURN)

        mv.visitLabel(l3)
        mv.visitLocalVariable(
            "resultSet",
            "Ljava/sql/ResultSet;",
            null,
            l0,
            l3,
            0
        )

        mv.visitLocalVariable(
            "target",
            target.byteCodeName(true),
            null,
            l1,
            l3,
            1
        )

        mv.visitMaxs(constructor.parameters.size * 2, 2)
        mv.visitEnd()
    }

    private fun emmitPropertyMapping(target: KClass<*>,
                                     mv: MethodVisitor,
                                     constructor: Constructor<*>,
                                     reader: DataReader) {
        val l0 = Label()
        val l1 = Label()
        val l2 = Label()
        val l3 = Label()

        mv.visitLabel(l0)

        if(constructor.canAccess(null)) {
            mv.visitTypeInsn(NEW, target.byteCodeName())
            mv.visitInsn(DUP)

            mv.visitMethodInsn(
                INVOKESPECIAL, target.byteCodeName(), "<init>", "()V", false
            )
        }
        else {
            mv.visitInvokeDynamicInsn(
                "createInstanceDynamic",
                "()${target.byteCodeName(true)}",
                Handle(
                    H_INVOKESTATIC,
                    "kapper/serialization/AsmMapper",
                    "bootstrapParameterlessConstructor",
                    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                )
            )
        }

        mv.visitVarInsn(ASTORE, 1)

        mv.visitLabel(l1)

        val properties = target.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>()

        reader.columns.forEach { column ->
            val property = properties.firstOrNull { it.name.equals(column, true) }

            if(property != null) {
                val methodEntry = methodTable[property.returnType] ?: throw IllegalArgumentException("Unsupported property type ${property.returnType}")

                mv.visitVarInsn(ALOAD, 1) //target
                mv.visitVarInsn(ALOAD, 0) // resultSet
                mv.visitLdcInsn(column) //stack is [columnName][resultSet][target]

                //resultSet.get[String,Int,Etc](columnName)
                mv.visitMethodInsn(
                    INVOKEINTERFACE,
                    "java/sql/ResultSet",
                    methodEntry.methodName,
                    methodEntry.getMethodDesc,
                    true)

                //stack is [returnValue][target]

                if(property.returnType.isMarkedNullable && methodEntry.nullableEntry != null) {
                    mv.visitMethodInsn(
                        INVOKESTATIC,
                        methodEntry.nullableEntry.owner,
                        methodEntry.nullableEntry.methodName,
                        methodEntry.nullableEntry.methodDesc,
                        false
                    )
                    //stack is [nullable type value][target]
                }

                //target.SetProp(returnValue)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    target.byteCodeName(),
                    property.setter.javaMethod?.name ?: throw IllegalArgumentException("Unable to obtain setter method for ${property.name}"),
                    methodEntry.setMethodDesc,
                    false)

                //stack is empty
            }
        }

        //L2
        mv.visitLabel(l2)
        mv.visitVarInsn(ALOAD, 1)
        mv.visitInsn(ARETURN)

        //L4
        mv.visitLabel(l3)
        mv.visitLocalVariable(
            "resultSet",
            "Ljava/sql/ResultSet;",
            null,
            l0,
            l3,
            0
        )

        mv.visitLocalVariable(
            "target",
            target.byteCodeName(true),
            null,
            l1,
            l3,
            1
        )

        mv.visitMaxs(3, 2)
        mv.visitEnd()
    }

    private fun findMatchConstructor(target: KClass<*>, reader: DataReader): KFunction<*>? {
        for(ctor in target.constructors.filter { it.parameters.size == reader.columns.size && it.visibility == KVisibility.PUBLIC }) {
            val parameters = ctor.parameters.sortedWith(compareBy(ColumnNameComparator) { it.name ?: "" })

            val math = run {
                var found = true

                for (index in parameters.indices) {
                    if (!parameters[index].name.equals(reader.columns[index], true)) {
                        found = false
                        break
                    }
                }

                found
            }
            if(math) return ctor
        }
        return null
    }

    private fun findParameterlessConstructor(target: KClass<*>): Constructor<*>? {
        //When using Kotlin reflection, the constructors property does not contains the constructors generated by
        //the Kotlin compiler, example is the constructors with default parameters
        //so we access the ".java" constructors to find those.
        return target.constructors.firstOrNull { it.parameters.isNullOrEmpty() }?.javaConstructor ?:
               target.java.constructors.firstOrNull { it.parameters.isNullOrEmpty() }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    @JvmStatic
    fun bootstrapParameterlessConstructor(lookup: MethodHandles.Lookup?, name: String?, type: MethodType?): CallSite {
        val ctor = type!!.returnType().declaredConstructors.first { it.parameters.isEmpty() }
        ctor.isAccessible = true

        // Convert Constructor to MethodHandle which will serve as a target of INVOKEDYNAMIC
        val mh = lookup!!.unreflectConstructor(ctor)
        return ConstantCallSite(mh)
    }
}

private data class TableEntry(val methodName: String,
                              val getMethodDesc: String,
                              val setMethodDesc: String,
                              val nullableEntry: NullableEntry? = null)

private data class NullableEntry(val owner: String,
                                 val methodName: String,
                                 val methodDesc: String)

private fun KClass<*>.byteCodeName(asDescriptor: Boolean = false): String {
    val name: String = when {
        qualifiedName != null -> qualifiedName!!
        simpleName != null -> simpleName!!
        else -> throw IllegalArgumentException("Unable to obtain class name.")
    }

    return name.byteCodeName(asDescriptor)
}

private fun KType.byteCodeName(asDescriptor: Boolean): String {
    return when(this) {
        KTypeCache.KInt -> "I"
        KTypeCache.KByte -> "B"
        KTypeCache.KShort -> "S"
        KTypeCache.KFloat -> "F"
        KTypeCache.KDouble -> "D"
        KTypeCache.KLong -> "J"
        KTypeCache.KBoolean -> "Z"
        else -> this.javaType.typeName.byteCodeName(asDescriptor)
    }
}

private fun String.byteCodeName(asDescriptor: Boolean): String {
    val name = this.replace('.', '/')
    return if(asDescriptor) "L$name;" else name
}

private object KTypeCache {
    val KString = String::class.createType()
    val KStringNullable = String::class.createType().withNullability(true)

    val KInt = Int::class.createType()
    val KIntNullable = Int::class.createType().withNullability(true)

    val KBoolean = Boolean::class.createType()
    val KBooleanNullable = Boolean::class.createType().withNullability(true)

    val KByte = Byte::class.createType()
    val KByteNullable = Byte::class.createType().withNullability(true)

    val KDate = Date::class.createType()
    val KDateNullable = Date::class.createType().withNullability(true)

    val KDouble = Double::class.createType()
    val KDoubleNullable = Double::class.createType().withNullability(true)

    val KFloat = Float::class.createType()
    val KFloatNullable = Float::class.createType().withNullability(true)

    val KLong = Long::class.createType()
    val KLongNullable = Long::class.createType().withNullability(true)

    val KBigDecimal = BigDecimal::class.createType()
    val KBigDecimalNullable = BigDecimal::class.createType().withNullability(true)

    val KShort = Short::class.createType()
    val KShortNullable = Short::class.createType().withNullability(true)
}
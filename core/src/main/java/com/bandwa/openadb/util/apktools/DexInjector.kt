package com.bandwa.openadb.util.apktools

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.json.JSONObject

/** 按类/方法/锚点向已解析的 dex 指令序列插入调用，不依赖 smali 文本。 */
object DexInjector {
    data class OpSpec(
        val opcode: String,
        val dst: String? = null,
        val obj: String? = null,
        val field: String? = null,
        val value: String? = null,
        val callee: String? = null,
        val args: List<String> = emptyList(),
    )

    data class Anchor(
        val opcode: String,
        val field: String? = null,
        val method: String? = null,
        val string: String? = null,
    )

    data class Injection(
        val classType: String,
        val methodName: String,
        val proto: String,
        val at: Anchor?,
        val ops: List<OpSpec>,
    )

    fun parse(array: org.json.JSONArray): List<Injection> {
        val result = ArrayList<Injection>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val at = when (val raw = o.opt("at")) {
                null, "start" -> null
                is JSONObject -> Anchor(
                    opcode = raw.getString("op"),
                    field = raw.optString("field").ifEmpty { null },
                    method = raw.optString("method").ifEmpty { null },
                    string = raw.optString("string").ifEmpty { null },
                )
                else -> throw IllegalArgumentException("unknown anchor: $raw")
            }
            val ops = ArrayList<OpSpec>()
            val opsArray = o.getJSONArray("ops")
            for (j in 0 until opsArray.length()) {
                val op = opsArray.getJSONObject(j)
                val args = ArrayList<String>()
                op.optJSONArray("args")?.let { a -> for (k in 0 until a.length()) args.add(a.getString(k)) }
                ops.add(
                    OpSpec(
                        opcode = op.getString("op"),
                        dst = op.optString("dst").ifEmpty { null },
                        obj = op.optString("obj").ifEmpty { null },
                        field = op.optString("field").ifEmpty { null },
                        value = op.optString("value").ifEmpty { null },
                        callee = op.optString("callee").ifEmpty { null },
                        args = args,
                    )
                )
            }
            result.add(
                Injection(
                    classType = o.getString("class"),
                    methodName = o.getString("method"),
                    proto = o.getString("proto"),
                    at = at,
                    ops = ops,
                )
            )
        }
        return result
    }

    fun apply(classDefs: Map<String, ClassDef>, injections: List<Injection>): Map<String, ClassDef> {
        if (injections.isEmpty()) return classDefs
        val result = HashMap(classDefs)
        for (injection in injections) {
            val classDef = result[injection.classType] ?: continue
            result[injection.classType] = inject(classDef, injection)
        }
        return result
    }

    private fun inject(classDef: ClassDef, injection: Injection): ClassDef {
        val method = classDef.methods.firstOrNull {
            it.name == injection.methodName && paramsOf(it) + it.returnType == injection.proto
        } ?: throw IllegalStateException("Injection failed, method not found: ${injection.classType}->${injection.methodName}${injection.proto}")

        val original = method.implementation
            ?: throw IllegalStateException("Injection failed, method has no implementation: ${injection.classType}->${injection.methodName}")
        val impl = MutableMethodImplementation(original)
        val instructions = impl.instructions

        if (injection.at == null && injection.ops.any { it.callee != null && instructions.any { ins -> sameMethod(ins, it.callee) } }) {
            return classDef
        }

        val index = if (injection.at == null) 0 else {
            val found = instructions.indexOfFirst { matches(it, injection.at) }
            if (found < 0) throw IllegalStateException(
                "注入失败，找不到锚点 ${injection.at.opcode} ${injection.at.field ?: injection.at.method ?: injection.at.string} " +
                    "in ${injection.classType}->${injection.methodName}"
            )
            found + 1
        }
        if (injection.at == null && original.tryBlocks.any { it.startCodeAddress <= 0 }) {
            throw IllegalStateException("Injection failed, method entry is inside a try block: ${injection.classType}->${injection.methodName}")
        }

        val paramRegisters = parameterRegisters(method)
        val built = injection.ops.map { build(it, impl.registerCount, paramRegisters) }
        built.forEachIndexed { offset, instruction -> impl.addInstruction(index + offset, instruction) }

        val patchedMethod = ImmutableMethod(
            method.definingClass,
            method.name,
            method.parameters,
            method.returnType,
            method.accessFlags,
            method.annotations,
            method.hiddenApiRestrictions,
            impl,
        )
        val patchedMethods = classDef.methods.mapTo(LinkedHashSet()) {
            if (it.name == method.name && it.returnType == method.returnType &&
                it.parameters.map { p -> p.type } == method.parameters.map { p -> p.type }
            ) patchedMethod else it
        }
        val result = ImmutableClassDef(
            classDef.type,
            classDef.accessFlags,
            classDef.superclass,
            classDef.interfaces,
            classDef.sourceFile,
            classDef.annotations,
            classDef.staticFields,
            classDef.instanceFields,
            patchedMethods.filter { isDirect(it) },
            patchedMethods.filter { !isDirect(it) },
        )
        return result
    }

    private fun isDirect(method: Method): Boolean =
        method.accessFlags and (AccessFlags.STATIC.value or AccessFlags.PRIVATE.value or AccessFlags.CONSTRUCTOR.value) != 0

    private fun paramsOf(method: Method): String = buildString {
        append('(')
        method.parameterTypes.forEach { append(it) }
        append(')')
    }

    private fun parameterRegisters(method: Method): Int {
        var count = if (method.accessFlags and AccessFlags.STATIC.value != 0) 0 else 1
        for (p in method.parameterTypes) count += if (p == "J" || p == "D") 2 else 1
        return count
    }

    private fun resolve(register: String, registerCount: Int, paramRegisters: Int): Int = when {
        register.startsWith("p") -> registerCount - paramRegisters + register.substring(1).toInt()
        register.startsWith("v") -> register.substring(1).toInt()
        else -> register.toInt()
    }

    private fun build(op: OpSpec, registerCount: Int, paramRegisters: Int): BuilderInstruction {
        val opcode = Opcode.valueOf(op.opcode.uppercase().replace('-', '_'))
        return when (opcode) {
            Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE -> {
                val ref = methodReference(op.callee ?: throw IllegalArgumentException("callee required"))
                val registers = op.args.map { resolve(it, registerCount, paramRegisters) }
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, registers.size,
                    registers.getOrElse(0) { 0 }, registers.getOrElse(1) { 0 },
                    registers.getOrElse(2) { 0 }, registers.getOrElse(3) { 0 },
                    registers.getOrElse(4) { 0 }, ref,
                )
            }
            Opcode.IGET_OBJECT, Opcode.IGET, Opcode.SGET_OBJECT -> {
                val field = fieldReference(op.field ?: throw IllegalArgumentException("field required"))
                val dst = resolve(op.dst ?: "v0", registerCount, paramRegisters)
                val obj = resolve(op.obj ?: "p0", registerCount, paramRegisters)
                if (opcode == Opcode.SGET_OBJECT) BuilderInstruction21c(opcode, dst, field)
                else BuilderInstruction22c(opcode, dst, obj, field)
            }
            Opcode.CONST_STRING -> {
                val dst = resolve(op.dst ?: "v0", registerCount, paramRegisters)
                BuilderInstruction21c(opcode, dst, ImmutableStringReference(op.value ?: ""))
            }
            else -> throw IllegalArgumentException("unsupported opcode: ${op.opcode}")
        }
    }

    private fun methodReference(value: String): ImmutableMethodReference {
        val arrow = value.indexOf("->")
        val defining = value.substring(0, arrow)
        val rest = value.substring(arrow + 2)
        val open = rest.indexOf('(')
        val name = rest.substring(0, open)
        val close = rest.lastIndexOf(')')
        val params = rest.substring(open + 1, close)
        val returnType = rest.substring(close + 1)
        return ImmutableMethodReference(defining, name, parseTypes(params), returnType)
    }

    private fun fieldReference(value: String): ImmutableFieldReference {
        val arrow = value.indexOf("->")
        val defining = value.substring(0, arrow)
        val rest = value.substring(arrow + 2)
        val colon = rest.indexOf(':')
        return ImmutableFieldReference(defining, rest.substring(0, colon), rest.substring(colon + 1))
    }

    private fun parseTypes(params: String): List<String> {
        val types = ArrayList<String>()
        var i = 0
        while (i < params.length) {
            val start = i
            while (params[i] == '[') i++
            if (params[i] == 'L') {
                i = params.indexOf(';', i) + 1
            } else {
                i++
            }
            types.add(params.substring(start, i))
        }
        return types
    }

    private fun sameMethod(instruction: BuilderInstruction, callee: String): Boolean {
        val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
        val expected = methodReference(callee)
        return ref.definingClass == expected.definingClass &&
            ref.name == expected.name &&
            ref.returnType == expected.returnType &&
            ref.parameterTypes.toList() == expected.parameterTypes.toList()
    }

    private fun matches(instruction: BuilderInstruction, anchor: Anchor): Boolean {
        if (instruction.opcode.name.lowercase().replace('_', '-') != anchor.opcode.lowercase().replace('_', '-')) return false
        val ref = (instruction as? ReferenceInstruction)?.reference
        return when {
            anchor.field != null -> ref is FieldReference &&
                ref.definingClass + "->" + ref.name + ":" + ref.type == anchor.field
            anchor.method != null -> ref is MethodReference &&
                ref.definingClass + "->" + ref.name + "(" + ref.parameterTypes.joinToString("") + ")" + ref.returnType == anchor.method
            anchor.string != null -> ref is StringReference && ref.string == anchor.string
            else -> true
        }
    }
}

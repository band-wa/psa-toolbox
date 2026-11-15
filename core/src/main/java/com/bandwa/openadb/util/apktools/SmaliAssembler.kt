package com.bandwa.openadb.util.apktools

import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import java.io.File

object SmaliAssembler {
    fun assembleToClassDefs(smaliFiles: List<File>, opcodes: Opcodes): Map<String, ClassDef> {
        require(smaliFiles.isNotEmpty()) { "smali file list is empty" }
        val workDir = createTempDir(prefix = "smali_asm_")
        try {
            val outDex = File(workDir, "out.dex")
            val options = SmaliOptions().apply {
                outputDexFile = outDex.absolutePath
                apiLevel = opcodes.api
            }
            val ok = Smali.assemble(options, smaliFiles.map { it.absolutePath })
            if (!ok || !outDex.isFile) {
                throw IllegalStateException("smali assembly failed: ${smaliFiles.map { it.name }}")
            }
            val dex = DexFileFactory.loadDexFile(outDex, opcodes)
            return dex.classes.associateBy { it.type }
        } finally {
            workDir.deleteRecursively()
        }
    }
}

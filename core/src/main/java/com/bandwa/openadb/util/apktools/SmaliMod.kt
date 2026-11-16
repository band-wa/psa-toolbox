package com.bandwa.openadb.util.apktools

import java.io.File

class SmaliMod {
    companion object {
        private fun readNormalized(file: File): String =
            file.readText().replace("\r\n", "\n")

        fun replaceValue(file: File, oldValue: String, newValue: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                if (!content.contains(oldValue)) {
                    return false
                }
                val modifiedContent = content.replace(oldValue, newValue)
                file.writeText(modifiedContent)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun replaceValueIfExists(file: File, checkValue: String, oldValue: String, newValue: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                if (content.contains(checkValue)) {
                    val modifiedContent = content.replace(oldValue, newValue)
                    file.writeText(modifiedContent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        fun checkValueExist(file: File, value: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                content.contains(value)
            } catch (e: Exception) {
                false
            }
        }

        fun replaceBetween(file: File, startMarker: String, endMarker: String, newContent: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val startIdx = content.indexOf(startMarker)
                if (startIdx == -1) return false

                val endIdx = content.indexOf(endMarker, startIdx + startMarker.length)
                if (endIdx == -1) return false

                val modifiedContent = content.replaceRange(startIdx + startMarker.length, endIdx, newContent)

                file.writeText(modifiedContent)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun replaceRegex(file: File, pattern: String, newValue: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val regex = Regex(pattern)
                if (!regex.containsMatchIn(content)) {
                    return false
                }
                file.writeText(regex.replace(content, newValue))
                true
            } catch (e: Exception) {
                false
            }
        }

        /** 把片段插到某方法声明行之前（新增一个方法）。 */
        fun insertBeforeMethod(file: File, methodDecl: String, frag: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val m = content.indexOf(methodDecl)
                if (m < 0) return false
                val lineStart = content.lastIndexOf('\n', m) + 1
                file.writeText(content.substring(0, lineStart) + frag + content.substring(lineStart))
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 把片段插到方法体的寄存器声明（.registers/.locals）之后。
         *
         * 会跳过紧跟其后的 `.annotation … .end annotation` 块：指令必须落在注解之后，
         * 否则 smali 解析报错；也不能越过标签，避免别处的跳转把插桩跳过去。
         */
        fun insertAfterRegisters(file: File, methodDecl: String, frag: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val m = content.indexOf(methodDecl)
                if (m < 0) return false
                val reg = content.indexOf(".registers", m)
                val loc = content.indexOf(".locals", m)
                val anchor = if (reg >= 0 && (loc < 0 || reg < loc)) reg else loc
                if (anchor < 0) return false
                var pos = content.indexOf('\n', anchor)
                if (pos < 0) return false
                pos += 1
                while (pos < content.length) {
                    val lineEnd = content.indexOf('\n', pos).let { if (it < 0) content.length else it }
                    val trimmed = content.substring(pos, lineEnd).trimStart()
                    when {
                        trimmed.startsWith(".annotation") -> {
                            val end = content.indexOf(".end annotation", pos)
                            if (end < 0) return false
                            pos = content.indexOf('\n', end).let { if (it < 0) content.length else it + 1 }
                        }
                        trimmed.isEmpty() || trimmed.startsWith("#") -> {
                            pos = lineEnd + 1
                        }
                        else -> break
                    }
                }
                if (pos > content.length) return false
                var f = frag
                if (f.startsWith("\r\n")) f = f.substring(2) else if (f.startsWith("\n")) f = f.substring(1)
                if (!f.endsWith("\n")) f = "$f\n"
                file.writeText(content.substring(0, pos) + f + content.substring(pos))
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 追加方法块到类末尾。
         *
         * 不依赖 `.end class` —— 运行期用 dexlib2 反汇编出来的类未必带这行，找不到就追加到末尾。
         */
        fun appendToClass(file: File, frag: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val e = content.lastIndexOf(".end class")
                val body = if (e >= 0) content.substring(0, e) + frag + content.substring(e) else {
                    val sb = StringBuilder(content)
                    if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') sb.append('\n')
                    sb.append(frag)
                    if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') sb.append('\n')
                    sb.toString()
                }
                file.writeText(body)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun replaceFile(file: File, newContent: String): Boolean {
            if (file.exists() && !file.isFile) {
                return false
            }

            return try {
                file.writeText(newContent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

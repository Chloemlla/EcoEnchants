package com.willfp.ecoenchants.backend

import java.util.regex.Pattern

object BackendJson {
    fun toJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${toJson(key.toString())}:${toJson(item)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
        else -> "\"${escape(value.toString())}\""
    }

    fun stringField(json: String, name: String): String? {
        val pattern = Regex(""""${Pattern.quote(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return pattern.find(json)?.groupValues?.get(1)?.let(::unescape)
    }

    fun longField(json: String, name: String): Long? {
        val pattern = Regex(""""${Pattern.quote(name)}"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    fun booleanField(json: String, name: String): Boolean? {
        val pattern = Regex(""""${Pattern.quote(name)}"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        return pattern.find(json)?.groupValues?.get(1)?.lowercase()?.toBooleanStrictOrNull()
    }

    fun stringArrayField(json: String, name: String): List<String> {
        val pattern = Regex(""""${Pattern.quote(name)}"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val body = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(body)
            .map { unescape(it.groupValues[1]) }
            .toList()
    }

    fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                result.append(char)
                index++
                continue
            }

            val escaped = value[index + 1]
            when (escaped) {
                '"' -> result.append('"')
                '\\' -> result.append('\\')
                '/' -> result.append('/')
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    val hex = value.substring(index + 2, (index + 6).coerceAtMost(value.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null && hex.length == 4) {
                        result.append(code.toChar())
                        index += 4
                    } else {
                        result.append("\\u")
                    }
                }
                else -> result.append(escaped)
            }
            index += 2
        }
        return result.toString()
    }
}

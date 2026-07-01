package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.Location

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters. For example, use `&lt;` instead of `<` \
                and `&amp;&amp;` instead of `&&` in data binding expressions.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun beforeCheckFile(context: Context) {
        val file = context.file
        if (!file.name.endsWith(".xml", ignoreCase = true)) {
            return
        }

        val parentName = file.parentFile?.name ?: ""
        if (!parentName.startsWith("layout")) {
            return
        }

        val contents = context.getContents() ?: return
        val length = contents.length
        var index = 0

        while (index < length) {
            val atSign = contents.indexOf('@', index)
            if (atSign == -1) break

            var startOfBrace = -1
            var exprStart = -1
            if (atSign + 1 < length && contents[atSign + 1] == '{') {
                startOfBrace = atSign + 1
                exprStart = atSign + 2
            } else if (atSign + 2 < length && contents[atSign + 1] == '=' && contents[atSign + 2] == '{') {
                startOfBrace = atSign + 2
                exprStart = atSign + 3
            }

            if (startOfBrace != -1) {
                var depth = 1
                var end = startOfBrace + 1
                while (end < length && depth > 0) {
                    val c = contents[end]
                    if (c == '{') {
                        depth++
                    } else if (c == '}') {
                        depth--
                    }
                    end++
                }

                if (depth == 0) {
                    var i = exprStart
                    val limit = end - 1
                    while (i < limit) {
                        val c = contents[i]
                        if (c == '<') {
                            val location = Location.create(context.file, contents, i, i + 1)
                            context.report(
                                ISSUE,
                                location,
                                "Must escape `<` as `&lt;` in XML attributes"
                            )
                        } else if (c == '&') {
                            if (!isValidEntity(contents, i, limit)) {
                                val location = Location.create(context.file, contents, i, i + 1)
                                context.report(
                                    ISSUE,
                                    location,
                                    "Must escape `&` as `&amp;` in XML attributes"
                                )
                            }
                        }
                        i++
                    }
                    index = end
                } else {
                    index = atSign + 1
                }
            } else {
                index = atSign + 1
            }
        }
    }

    private fun isValidEntity(contents: CharSequence, index: Int, limit: Int): Boolean {
        var j = index + 1
        while (j < limit && contents[j] != ';') {
            val c = contents[j]
            if (!c.isLetterOrDigit() && c != '#' && c != '_' && c != '-') {
                return false
            }
            j++
        }
        if (j >= limit || contents[j] != ';') {
            return false
        }
        return j > index + 1
    }
}
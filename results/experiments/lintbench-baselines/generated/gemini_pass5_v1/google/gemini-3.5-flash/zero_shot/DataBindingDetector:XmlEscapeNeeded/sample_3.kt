package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class DataBindingDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun beforeCheckFile(context: Context) {
        if (context !is XmlContext) return
        val contents = context.getContents() ?: return

        var index = 0
        while (index < contents.length) {
            val start = contents.indexOf("@{", index)
            val startTwoWay = contents.indexOf("@={", index)
            val exprStart = if (start != -1 && startTwoWay != -1) {
                minOf(start, startTwoWay)
            } else if (start != -1) {
                start
            } else if (startTwoWay != -1) {
                startTwoWay
            } else {
                break
            }

            var braceCount = 0
            var exprEnd = -1
            for (i in exprStart until contents.length) {
                val c = contents[i]
                if (c == '{') {
                    braceCount++
                } else if (c == '}') {
                    braceCount--
                    if (braceCount == 0) {
                        exprEnd = i
                        break
                    }
                }
            }

            if (exprEnd == -1) {
                index = exprStart + 2
                continue
            }

            val expression = contents.substring(exprStart, exprEnd + 1)

            // Check for '<'
            if (expression.contains("<")) {
                val offset = exprStart + expression.indexOf("<")
                val location = Location.create(context.file, contents, offset, offset + 1)
                context.report(
                    ISSUE,
                    location,
                    "Must escape `<` as `&lt;` in XML attributes"
                )
            }

            // Check for unescaped '&'
            val ampIndex = findUnescapedAmpersandIndex(expression)
            if (ampIndex != -1) {
                val offset = exprStart + ampIndex
                val location = Location.create(context.file, contents, offset, offset + 1)
                context.report(
                    ISSUE,
                    location,
                    "Must escape `&` as `&amp;` in XML attributes"
                )
            }

            // Find conflicting quotes
            var attrQuote: Char? = null
            for (i in exprStart - 1 downTo 0) {
                val c = contents[i]
                if (c == '"' || c == '\'') {
                    var foundEquals = false
                    for (j in i - 1 downTo 0) {
                        val c2 = contents[j]
                        if (c2.isWhitespace()) continue
                        if (c2 == '=') {
                            foundEquals = true
                        }
                        break
                    }
                    if (foundEquals) {
                        attrQuote = c
                        break
                    }
                }
            }

            if (attrQuote != null) {
                val quoteIndex = expression.indexOf(attrQuote)
                if (quoteIndex != -1) {
                    val offset = exprStart + quoteIndex
                    val location = Location.create(context.file, contents, offset, offset + 1)
                    val escapeStr = if (attrQuote == '"') "&quot;" else "&apos;"
                    context.report(
                        ISSUE,
                        location,
                        "Quote character `$attrQuote` conflicts with attribute delimiter; use `$escapeStr` or swap outer and inner quotes"
                    )
                }
            }

            index = exprEnd + 1
        }
    }

    private fun findUnescapedAmpersandIndex(s: String): Int {
        var i = 0
        while (i < s.length) {
            if (s[i] == '&') {
                val remains = s.substring(i)
                val isEscaped = remains.startsWith("&amp;") ||
                        remains.startsWith("&lt;") ||
                        remains.startsWith("&gt;") ||
                        remains.startsWith("&quot;") ||
                        remains.startsWith("&apos;") ||
                        remains.startsWith("&nbsp;") ||
                        remains.matches(Regex("^&#\\d+;.*")) ||
                        remains.matches(Regex("^&#x[0-9a-fA-F]+;.*"))
                if (!isEscaped) {
                    return i
                }
            }
            i++
        }
        return -1
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
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
}
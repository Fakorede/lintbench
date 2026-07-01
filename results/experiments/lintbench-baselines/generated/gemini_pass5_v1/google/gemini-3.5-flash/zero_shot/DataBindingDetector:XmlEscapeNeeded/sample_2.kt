package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import java.util.EnumSet

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun run(context: Context) {
        if (context !is XmlContext) return
        val contents = context.getContents() ?: return
        val text = contents.toString()

        var index = 0
        while (index < text.length) {
            val start = text.indexOf("@{", index)
            val startTwo = text.indexOf("@={", index)
            val exprStart = if (start != -1 && startTwo != -1) {
                minOf(start, startTwo)
            } else if (start != -1) {
                start
            } else {
                startTwo
            }
            if (exprStart == -1) break

            val isTwoWay = exprStart == startTwo
            val contentStart = exprStart + (if (isTwoWay) 3 else 2)

            // Find matching '}' taking quotes into account
            var braceCount = 1
            var i = contentStart
            var end = -1
            var inSingleQuote = false
            var inDoubleQuote = false
            while (i < text.length) {
                val c = text[i]
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote
                } else if (!inSingleQuote && !inDoubleQuote) {
                    if (c == '{') {
                        braceCount++
                    } else if (c == '}') {
                        braceCount--
                        if (braceCount == 0) {
                            end = i
                            break
                        }
                    }
                }
                i++
            }

            if (end != -1) {
                val attributeQuote = findAttributeQuote(text, exprStart)

                // Scan the expression inside the raw XML for unescaped characters
                var k = contentStart
                while (k < end) {
                    val c = text[k]
                    var errorMsg: String? = null

                    if (c == '<') {
                        errorMsg = "Must escape '<' as '&lt;'"
                    } else if (c == '&') {
                        if (!isValidEntity(text, k)) {
                            errorMsg = "Must escape '&' as '&amp;'"
                        }
                    } else if (attributeQuote != null && c == attributeQuote) {
                        val escapeStr = if (c == '"') "&quot;" else "&apos;"
                        errorMsg = "Must escape '$c' as '$escapeStr' when attribute is enclosed in $c"
                    }

                    if (errorMsg != null) {
                        val location = Location.create(context.file, contents, k, k + 1)
                        context.report(
                            ISSUE,
                            location,
                            errorMsg
                        )
                    }
                    k++
                }
            }
            index = exprStart + 1
        }
    }

    private fun findAttributeQuote(text: String, exprStart: Int): Char? {
        var i = exprStart - 1
        while (i >= 0) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                return c
            }
            if (c == '<' || c == '>') {
                return null
            }
            i--
        }
        return null
    }

    private fun isValidEntity(text: String, startIndex: Int): Boolean {
        if (text[startIndex] != '&') return false
        val semi = text.indexOf(';', startIndex)
        if (semi == -1 || semi - startIndex > 10) return false
        val entity = text.substring(startIndex + 1, semi)
        if (entity == "lt" || entity == "gt" || entity == "amp" || entity == "quot" || entity == "apos") return true
        if (entity.startsWith("#")) {
            val num = entity.substring(1)
            if (num.startsWith("x") || num.startsWith("X")) {
                val hex = num.substring(1)
                return hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            }
            return num.all { it.isDigit() }
        }
        return false
    }
}
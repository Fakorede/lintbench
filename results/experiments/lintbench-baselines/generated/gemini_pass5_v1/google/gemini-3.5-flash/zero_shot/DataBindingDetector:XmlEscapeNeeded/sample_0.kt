package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document

class DataBindingDetector : Detector(), XmlScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters. For example, use `&lt;` instead of `<` \
                and `&amp;` instead of `&` inside data binding expressions.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.LAYOUT_RESOURCE_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val contents = context.getContents()?.toString() ?: return
        
        var i = 0
        val len = contents.length
        while (i < len) {
            if (contents.startsWith("@{", i) || contents.startsWith("@={", i)) {
                val isTwoWay = contents.startsWith("@={", i)
                val exprStart = if (isTwoWay) i + 3 else i + 2
                
                var braceCount = 1
                var j = exprStart
                var inSingleQuote = false
                var inDoubleQuote = false
                var escaped = false
                
                while (j < len && braceCount > 0) {
                    val c = contents[j]
                    if (escaped) {
                        escaped = false
                        j++
                        continue
                    }
                    if (c == '\\') {
                        escaped = true
                        j++
                        continue
                    }
                    if (c == '\'' && !inDoubleQuote) {
                        inSingleQuote = !inSingleQuote
                    } else if (c == '"' && !inSingleQuote) {
                        inDoubleQuote = !inDoubleQuote
                    } else if (!inSingleQuote && !inDoubleQuote) {
                        if (c == '{') {
                            braceCount++
                        } else if (c == '}') {
                            braceCount--
                        }
                    }
                    j++
                }
                
                if (braceCount == 0) {
                    val exprEnd = j - 1
                    analyzeExpression(context, contents, exprStart, exprEnd)
                }
                i = if (j > i) j else i + 1
            } else {
                i++
            }
        }
    }

    private fun analyzeExpression(
        context: XmlContext,
        contents: String,
        exprStart: Int,
        exprEnd: Int
    ) {
        val entityRegex = Regex("^&(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);")
        
        var i = exprStart
        while (i < exprEnd) {
            val c = contents[i]
            if (c == '<') {
                val charLocation = Location.create(context.file, contents, i, i + 1)
                context.report(
                    ISSUE,
                    charLocation,
                    "Must escape '<' as '&lt;'"
                )
            } else if (c == '&') {
                val remaining = contents.substring(i, exprEnd)
                val match = entityRegex.find(remaining)
                if (match == null) {
                    val charLocation = Location.create(context.file, contents, i, i + 1)
                    context.report(
                        ISSUE,
                        charLocation,
                        "Must escape '&' as '&amp;'"
                    )
                } else {
                    i += match.value.length - 1
                }
            }
            i++
        }
    }
}
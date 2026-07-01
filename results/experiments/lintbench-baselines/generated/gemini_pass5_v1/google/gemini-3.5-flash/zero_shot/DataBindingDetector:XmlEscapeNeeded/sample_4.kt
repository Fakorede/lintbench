package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import java.util.regex.Pattern

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters. " +
                    "In data binding expressions, characters like `<` and `&` must be escaped as `&lt;` and `&amp;` respectively.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ENTITY_PATTERN = Pattern.compile("^&([a-zA-Z]+|#[0-9]+|#x[0-9a-fA-F]+);")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val contents = context.getContents() ?: return

        var index = 0
        while (index < contents.length) {
            val start = findExpressionStart(contents, index)
            if (start == -1) break

            val end = findExpressionEnd(contents, start)
            if (end == -1) {
                index = start + 2
                continue
            }

            val expression = contents.substring(start, end)
            checkExpression(context, contents, expression, start)

            index = end
        }
    }

    private fun findExpressionStart(contents: CharSequence, startIndex: Int): Int {
        val idx1 = contents.indexOf("@{", startIndex)
        val idx2 = contents.indexOf("={", startIndex)
        if (idx1 == -1) return idx2
        if (idx2 == -1) return idx1
        return Math.min(idx1, idx2)
    }

    private fun findExpressionEnd(contents: CharSequence, start: Int): Int {
        var braceCount = 1
        var i = start + 2
        while (i < contents.length) {
            val c = contents[i]
            if (c == '{') {
                braceCount++
            } else if (c == '}') {
                braceCount--
                if (braceCount == 0) {
                    return i + 1
                }
            }
            i++
        }
        return -1
    }

    private fun checkExpression(context: XmlContext, contents: CharSequence, expression: String, expressionOffset: Int) {
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            if (c == '<') {
                val startOffset = expressionOffset + i
                val endOffset = startOffset + 1
                val location = Location.create(context.file, contents, startOffset, endOffset)
                context.report(
                    ISSUE,
                    location,
                    "Character `<` must be escaped as `&lt;`"
                )
            } else if (c == '&') {
                val sub = expression.substring(i)
                val matcher = ENTITY_PATTERN.matcher(sub)
                if (!matcher.find()) {
                    val startOffset = expressionOffset + i
                    val endOffset = startOffset + 1
                    val location = Location.create(context.file, contents, startOffset, endOffset)
                    context.report(
                        ISSUE,
                        location,
                        "Character `&` must be escaped as `&amp;`"
                    )
                } else {
                    i += matcher.end() - 1
                }
            }
            i++
        }
    }
}
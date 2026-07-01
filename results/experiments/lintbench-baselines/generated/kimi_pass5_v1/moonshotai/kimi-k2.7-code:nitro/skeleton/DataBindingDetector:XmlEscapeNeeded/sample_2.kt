package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, such as
                `<` and `&`, you must escape them using the corresponding XML entities
                (`&lt;` and `&amp;`). Failing to escape these characters can result in
                malformed XML or incorrect parsing.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = Detector.XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val contents = context.getContents() ?: return
        val location = context.getLocation(attribute)
        val startOffset = location.start?.offset ?: return
        val endOffset = location.end?.offset ?: return
        if (startOffset < 0 || endOffset > contents.length || endOffset <= startOffset) {
            return
        }

        val rawAttribute = contents.subSequence(startOffset, endOffset).toString()
        val rawValue = extractAttributeValue(rawAttribute) ?: return
        val (special) = findUnescapedSpecialCharacter(rawValue) ?: return

        context.report(
            ISSUE,
            attribute,
            context.getLocation(attribute),
            "The string contains '$special', which must be escaped as ${getXmlEntity(special)}."
        )
    }

    private fun extractAttributeValue(rawAttribute: String): String? {
        val equalsIndex = rawAttribute.indexOf('=')
        if (equalsIndex == -1 || equalsIndex + 1 >= rawAttribute.length) {
            return null
        }
        val quote = rawAttribute[equalsIndex + 1]
        if (quote != '"' && quote != '\'') {
            return null
        }
        val endIndex = rawAttribute.lastIndexOf(quote)
        if (endIndex <= equalsIndex + 1) {
            return null
        }
        return rawAttribute.substring(equalsIndex + 2, endIndex)
    }

    private fun findUnescapedSpecialCharacter(rawValue: String): Pair<Char, Int>? {
        var i = 0
        while (i < rawValue.length) {
            val c = rawValue[i]
            when {
                c == '<' -> return c to i
                c == '&' -> {
                    val end = rawValue.indexOf(';', i)
                    if (end == -1) return c to i
                    val entity = rawValue.substring(i + 1, end)
                    if (!isValidEntityReference(entity)) {
                        return c to i
                    }
                    i = end + 1
                }
                else -> i++
            }
        }
        return null
    }

    private fun isValidEntityReference(entity: String): Boolean {
        if (entity.isEmpty()) return false
        if (entity[0] == '#') {
            if (entity.length == 1) return false
            if (entity[1] == 'x' || entity[1] == 'X') {
                val hex = entity.substring(2)
                return hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
            val decimal = entity.substring(1)
            return decimal.isNotEmpty() && decimal.all { it.isDigit() }
        }
        return entity.all { it.isLetterOrDigit() }
    }

    private fun getXmlEntity(c: Char): String {
        return when (c) {
            '<' -> "&lt;"
            '>' -> "&gt;"
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> c.toString()
        }
    }
}
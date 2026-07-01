package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
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
                When a string (such as a data binding expression in a layout attribute) contains
                characters that have special meaning in XML, those characters must be escaped.

                For example:
                - `<` must be written as `&lt;`
                - `>` should be written as `&gt;`
                - `&` must be written as `&amp;`

                Failing to escape these characters can cause XML parsing errors or unexpected
                behavior in data binding.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (value.isEmpty()) return

        val rawValue = getRawAttributeValue(context, attribute) ?: return
        val unescaped = findFirstUnescapedSpecialCharacter(rawValue)
        if (unescaped != null) {
            val message = "The XML character '$unescaped' must be escaped; " +
                "for example, use ${escapeHint(unescaped)}"
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message)
        }
    }

    private fun getRawAttributeValue(context: XmlContext, attribute: Attr): String? {
        val contents = context.getContents() ?: return null
        val location: Location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return null
        val end = location.end?.offset ?: return null
        if (start < 0 || end > contents.length || start >= end) return null
        return contents.subSequence(start, end).toString()
    }

    private fun findFirstUnescapedSpecialCharacter(raw: String): Char? {
        var i = 0
        while (i < raw.length) {
            when (val c = raw[i]) {
                '<' -> return c
                '&' -> {
                    val end = raw.indexOf(';', i + 1)
                    if (end == -1 || end == i + 1 || !isEntityReference(raw.substring(i + 1, end))) {
                        return c
                    }
                    i = end
                }
            }
            i++
        }
        return null
    }

    private fun isEntityReference(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name[0] == '#') {
            if (name.length == 1) return false
            val digits = if (name[1] == 'x' || name[1] == 'X') {
                name.substring(2)
            } else {
                name.substring(1)
            }
            if (digits.isEmpty()) return false
            return digits.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        }
        return name.all { it.isLetterOrDigit() }
    }

    private fun escapeHint(c: Char): String = when (c) {
        '<' -> "`&lt;`"
        '>' -> "`&gt;`"
        '&' -> "`&amp;`"
        else -> "the corresponding XML entity"
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Text

class DataBindingDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private val ATTRIBUTE_SPECIALS = setOf('<', '>', '&')
        private val TEXT_SPECIALS = setOf('<', '&')
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("*")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.contains("@{") && !value.contains("@={")) return

        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val raw = context.getContents().subSequence(start, end).toString()

        val content = raw.trim().removeSurrounding("\"").removeSurrounding("'")
        val unescaped = findUnescapedSpecialChar(content, ATTRIBUTE_SPECIALS) ?: return

        context.report(
            ISSUE,
            attribute,
            location,
            "Missing XML Escape: '$unescaped' must be escaped as ${escapeForXml(unescaped)}"
        )
    }

    override fun visitText(context: XmlContext, text: Text) {
        if (text.nodeValue.isNullOrBlank()) return

        val location = context.getLocation(text)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val raw = context.getContents().subSequence(start, end).toString()

        if (raw.trimStart().startsWith("<![CDATA[")) return

        val unescaped = findUnescapedSpecialChar(raw, TEXT_SPECIALS) ?: return

        context.report(
            ISSUE,
            text,
            location,
            "Missing XML Escape: '$unescaped' must be escaped as ${escapeForXml(unescaped)}"
        )
    }

    private fun findUnescapedSpecialChar(text: String, specials: Set<Char>): Char? {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c !in specials) {
                i++
                continue
            }

            if (c == '&') {
                val semicolon = text.indexOf(';', i + 1)
                if (semicolon == -1 || !isXmlEntity(text.substring(i + 1, semicolon))) {
                    return '&'
                }
                i = semicolon
            } else {
                return c
            }
            i++
        }
        return null
    }

    private fun isXmlEntity(entity: String): Boolean {
        if (entity.isEmpty()) return false
        if (entity[0] == '#') {
            val rest = entity.substring(1)
            if (rest.isEmpty()) return false
            return if (rest[0] == 'x' || rest[0] == 'X') {
                rest.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            } else {
                rest.all { it.isDigit() }
            }
        }
        return entity.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
    }

    private fun escapeForXml(c: Char): String = when (c) {
        '<' -> "&lt;"
        '>' -> "&gt;"
        '&' -> "&amp;"
        '"' -> "&quot;"
        '\'' -> "&apos;"
        else -> c.toString()
    }
}
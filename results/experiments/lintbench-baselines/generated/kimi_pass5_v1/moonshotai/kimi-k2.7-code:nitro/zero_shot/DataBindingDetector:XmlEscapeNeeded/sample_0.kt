package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                val location = context.getLocation(child)
                val rawValue = getRawValue(context, location)
                if (rawValue != null && rawValue.isNotBlank() && hasUnescapedXmlSpecialChar(rawValue)) {
                    context.report(
                        ISSUE,
                        element,
                        location,
                        "Missing XML escape: the string contains characters that must be escaped in XML"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val location = context.getValueLocation(attribute)
        val rawValue = getRawValue(context, location) ?: return
        if (hasUnescapedXmlSpecialChar(rawValue)) {
            context.report(
                ISSUE,
                attribute,
                location,
                "Missing XML escape: the string contains characters that must be escaped in XML"
            )
        }
    }

    private fun getRawValue(context: XmlContext, location: Location): String? {
        val start = location.start ?: return null
        val end = location.end ?: return null
        val contents = context.getContents() ?: return null
        return contents.subSequence(start.offset, end.offset).toString()
    }

    private fun hasUnescapedXmlSpecialChar(rawValue: String): Boolean {
        val isQuoted = rawValue.length >= 2 &&
            ((rawValue[0] == '"' && rawValue[rawValue.length - 1] == '"') ||
             (rawValue[0] == '\'' && rawValue[rawValue.length - 1] == '\''))
        val quoteChar = if (isQuoted) rawValue[0] else null
        val value = if (isQuoted) rawValue.substring(1, rawValue.length - 1) else rawValue

        var i = 0
        while (i < value.length) {
            when (val c = value[i]) {
                '<' -> return true
                '>' -> if (i >= 2 && value[i - 1] == ']' && value[i - 2] == ']') return true
                '&' -> {
                    val semi = value.indexOf(';', i)
                    if (semi == -1) return true
                    val entity = value.substring(i + 1, semi)
                    if (!isValidEntityReference(entity)) return true
                    i = semi
                }
                '"' -> if (quoteChar == '"') return true
                '\'' -> if (quoteChar == '\'') return true
            }
            i++
        }
        return false
    }

    private fun isValidEntityReference(entity: String): Boolean {
        return entity == "amp" || entity == "lt" || entity == "gt" ||
            entity == "quot" || entity == "apos" ||
            entity.matches(NUMERIC_ENTITY_PATTERN) ||
            entity.matches(HEX_ENTITY_PATTERN)
    }

    private val NUMERIC_ENTITY_PATTERN = Regex("#[0-9]+")
    private val HEX_ENTITY_PATTERN = Regex("#x[0-9a-fA-F]+")

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, you must escape the characters.
                For example, replace `&` with `&amp;`, `<` with `&lt;`, and `>` with `&gt;`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
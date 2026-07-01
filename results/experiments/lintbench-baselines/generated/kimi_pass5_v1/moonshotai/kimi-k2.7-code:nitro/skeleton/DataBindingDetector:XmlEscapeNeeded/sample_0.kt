package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
            explanation = "Data binding expressions are parsed after the XML parser has decoded the attribute value, so any characters that have special meaning in XML (such as < and &) must be escaped using the corresponding XML entities (&lt; and &amp;).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = Detector.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val rawValue = getRawAttributeValue(context, attribute) ?: return
        if (!isDataBindingExpression(rawValue)) return
        if (!containsUnescapedXmlCharacter(rawValue)) return

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "The data binding expression contains XML characters that must be escaped (e.g., use &lt; and &amp;)"
        )
    }

    private fun getRawAttributeValue(context: XmlContext, attribute: Attr): String? {
        val valueLocation: Location = context.getValueLocation(attribute) ?: return null
        val start = valueLocation.start ?: return null
        val end = valueLocation.end ?: return null
        val contents = context.getContents() ?: return null
        if (start.offset < 0 || end.offset > contents.length || start.offset >= end.offset) {
            return null
        }
        var s = start.offset
        var e = end.offset
        if (s < e && (contents[s] == '"' || contents[s] == '\'')) {
            s++
        }
        if (e > s && (contents[e - 1] == '"' || contents[e - 1] == '\'')) {
            e--
        }
        return contents.subSequence(s, e).toString()
    }

    private fun isDataBindingExpression(rawValue: String): Boolean {
        if (!rawValue.startsWith("@")) {
            return false
        }
        var i = 1
        if (i < rawValue.length && rawValue[i] == '=') {
            i++
        }
        return i < rawValue.length && rawValue[i] == '{'
    }

    private fun containsUnescapedXmlCharacter(rawValue: String): Boolean {
        var i = 0
        while (i < rawValue.length) {
            when (rawValue[i]) {
                '<' -> return true
                '&' -> {
                    val end = rawValue.indexOf(';', i + 1)
                    if (end == -1 || end - i > 30) {
                        return true
                    }
                    val entity = rawValue.substring(i + 1, end)
                    if (!isValidXmlEntityReference(entity)) {
                        return true
                    }
                    i = end + 1
                    continue
                }
            }
            i++
        }
        return false
    }

    private fun isValidXmlEntityReference(entity: String): Boolean {
        if (entity.isEmpty()) {
            return false
        }
        if (entity == "amp" || entity == "lt" || entity == "gt" ||
            entity == "quot" || entity == "apos"
        ) {
            return true
        }
        if (entity.startsWith("#")) {
            val rest = entity.substring(1)
            return if (rest.startsWith("x", ignoreCase = true)) {
                val hex = rest.substring(1)
                hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            } else {
                rest.isNotEmpty() && rest.all { it.isDigit() }
            }
        }
        return false
    }
}
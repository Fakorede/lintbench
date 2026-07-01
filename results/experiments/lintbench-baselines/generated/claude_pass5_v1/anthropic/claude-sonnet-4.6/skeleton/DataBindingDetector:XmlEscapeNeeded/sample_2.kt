package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
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
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val SPECIAL_CHARS = mapOf(
            '&' to "&amp;",
            '<' to "&lt;",
            '>' to "&gt;",
            '"' to "&quot;",
            '\'' to "&apos;"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Data binding expressions use @{ ... } syntax; skip those
        if (value.startsWith("@{") || value.startsWith("@={")) {
            return
        }

        // Check for unescaped special XML characters
        val unescapedChar = findUnescapedSpecialChar(value) ?: return

        val escaped = escapeXml(value)

        val fix = LintFix.create()
            .name("Escape XML characters")
            .replace()
            .text(value)
            .with(escaped)
            .build()

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "Attribute value contains unescaped XML character '${unescapedChar}'; " +
                "replace with the escaped entity",
            fix
        )
    }

    /**
     * Finds the first unescaped special XML character in the given string,
     * or returns null if no unescaped special characters are found.
     */
    private fun findUnescapedSpecialChar(value: String): Char? {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when (c) {
                '&' -> {
                    // Check if this is already an escape sequence like &amp; &lt; etc.
                    val semicolonIndex = value.indexOf(';', i + 1)
                    if (semicolonIndex != -1) {
                        val entity = value.substring(i + 1, semicolonIndex)
                        if (isKnownXmlEntity(entity)) {
                            // Already escaped, skip past the entire entity
                            i = semicolonIndex + 1
                            continue
                        }
                    }
                    return c
                }
                '<', '>' -> return c
                '"' -> {
                    // Only flag quotes if they appear inside attribute values
                    // (the XML parser would have already caught truly unescaped quotes,
                    // but flag them anyway for correctness)
                    return c
                }
                else -> { /* not a special character */ }
            }
            i++
        }
        return null
    }

    /**
     * Returns true if the given entity name (without & and ;) is a known XML entity.
     */
    private fun isKnownXmlEntity(entity: String): Boolean {
        return when (entity) {
            "amp", "lt", "gt", "quot", "apos" -> true
            else -> entity.startsWith("#") // numeric character references like &#160; or &#x00A0;
        }
    }

    /**
     * Escapes all special XML characters in the given string.
     */
    private fun escapeXml(value: String): String {
        val sb = StringBuilder(value.length + 16)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when (c) {
                '&' -> {
                    // Check if already an escape sequence
                    val semicolonIndex = value.indexOf(';', i + 1)
                    if (semicolonIndex != -1) {
                        val entity = value.substring(i + 1, semicolonIndex)
                        if (isKnownXmlEntity(entity)) {
                            // Already escaped — keep as-is
                            sb.append(value, i, semicolonIndex + 1)
                            i = semicolonIndex + 1
                            continue
                        }
                    }
                    sb.append("&amp;")
                }
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
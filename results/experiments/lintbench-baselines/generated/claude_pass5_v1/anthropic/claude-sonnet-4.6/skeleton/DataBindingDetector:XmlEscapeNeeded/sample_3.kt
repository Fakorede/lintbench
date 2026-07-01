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

        private val XML_SPECIAL_CHARS = setOf('<', '>', '&', '"', '\'')
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Only check data binding expressions (those that start with @{ or @={)
        val trimmed = value.trim()
        if (!trimmed.startsWith("@{") && !trimmed.startsWith("@={")) {
            return
        }

        // Check the raw XML text of the attribute value for unescaped special characters
        val rawText = getRawAttributeValue(context, attribute) ?: return

        // Find the actual attribute value portion (between the quotes in the XML source)
        // We need to check if the raw source contains unescaped XML special characters
        // that should have been escaped but weren't
        checkForUnescapedCharacters(context, attribute, rawText)
    }

    private fun getRawAttributeValue(context: XmlContext, attribute: Attr): String? {
        return try {
            val contents = context.getContents() ?: return null
            val location = context.getValueLocation(attribute)
            val start = location.start ?: return null
            val end = location.end ?: return null
            val startOffset = start.offset
            val endOffset = end.offset
            if (startOffset < 0 || endOffset < 0 || startOffset >= endOffset) return null
            if (endOffset > contents.length) return null
            contents.substring(startOffset, endOffset)
        } catch (e: Exception) {
            null
        }
    }

    private fun checkForUnescapedCharacters(context: XmlContext, attribute: Attr, rawValue: String) {
        // In XML attribute values, certain characters must be escaped:
        // & -> &amp;
        // < -> &lt;
        // > -> &gt; (technically optional but recommended)
        // " -> &quot; (if attribute is delimited by double quotes)
        // ' -> &apos; (if attribute is delimited by single quotes)

        // We look for patterns that indicate an unescaped character was used
        // The raw value here is the text between the quotes of the attribute

        var i = 0
        while (i < rawValue.length) {
            val ch = rawValue[i]
            when (ch) {
                '&' -> {
                    // Check if this is already an escape sequence like &amp; &lt; &gt; &quot; &apos; &#...;
                    if (!isEscapeSequence(rawValue, i)) {
                        reportUnescapedCharacter(context, attribute, ch, "&amp;")
                        return
                    }
                    // Skip past the escape sequence
                    val semicolonIndex = rawValue.indexOf(';', i)
                    if (semicolonIndex != -1) {
                        i = semicolonIndex + 1
                    } else {
                        i++
                    }
                    continue
                }
                '<' -> {
                    reportUnescapedCharacter(context, attribute, ch, "&lt;")
                    return
                }
                '>' -> {
                    reportUnescapedCharacter(context, attribute, ch, "&gt;")
                    return
                }
                else -> { /* no issue */ }
            }
            i++
        }
    }

    private fun isEscapeSequence(text: String, ampIndex: Int): Boolean {
        val rest = text.substring(ampIndex)
        return rest.startsWith("&amp;") ||
            rest.startsWith("&lt;") ||
            rest.startsWith("&gt;") ||
            rest.startsWith("&quot;") ||
            rest.startsWith("&apos;") ||
            rest.startsWith("&#")
    }

    private fun reportUnescapedCharacter(
        context: XmlContext,
        attribute: Attr,
        char: Char,
        escapedForm: String,
    ) {
        val message = "The character '$char' must be escaped in XML attribute values as `$escapedForm`"
        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            message,
        )
    }
}
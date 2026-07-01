package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String> {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (value.isBlank()) return

        // Check for unescaped characters that need XML escaping
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when (c) {
                '&' -> {
                    // Check if it's already a valid XML entity reference
                    val semicolonIndex = value.indexOf(';', i + 1)
                    if (semicolonIndex == -1) {
                        // No semicolon found, it's a bare ampersand
                        reportIssue(context, attribute, c)
                        return
                    }
                    val entity = value.substring(i + 1, semicolonIndex)
                    if (!isValidXmlEntity(entity)) {
                        reportIssue(context, attribute, c)
                        return
                    }
                    i = semicolonIndex + 1
                    continue
                }
                '<' -> {
                    reportIssue(context, attribute, c)
                    return
                }
                '>' -> {
                    reportIssue(context, attribute, c)
                    return
                }
                '"' -> {
                    // Unescaped double quote inside an attribute value is problematic
                    // only if the attribute is delimited by double quotes.
                    // The XML parser would have already caught truly malformed XML,
                    // but we check for cases that might slip through (e.g. in tools).
                    // We skip this here since the parser handles it.
                }
                else -> { /* fine */ }
            }
            i++
        }
    }

    private fun isValidXmlEntity(entity: String): Boolean {
        if (entity.isEmpty()) return false
        // Named entities defined in XML spec
        return when (entity) {
            "amp", "lt", "gt", "apos", "quot" -> true
            else -> {
                // Numeric character references: &#dddd; or &#xhhhh;
                if (entity.startsWith("#")) {
                    val ref = entity.substring(1)
                    if (ref.startsWith("x") || ref.startsWith("X")) {
                        // Hexadecimal reference
                        val hex = ref.substring(1)
                        hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                    } else {
                        // Decimal reference
                        ref.isNotEmpty() && ref.all { it.isDigit() }
                    }
                } else {
                    false
                }
            }
        }
    }

    private fun reportIssue(context: XmlContext, attribute: Attr, character: Char) {
        val escapedChar = when (character) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> "&#${character.code};"
        }
        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "Attribute value contains character `$character` that must be escaped as `$escapedChar`"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation =
                "When a string contains characters that have special usage in XML, " +
                "you must escape the characters. For example, if your string contains " +
                "an ampersand (`&`), you must escape it as `&amp;`. Similarly, " +
                "a less-than sign (`<`) must be escaped as `&lt;`, and a greater-than " +
                "sign (`>`) must be escaped as `&gt;`.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
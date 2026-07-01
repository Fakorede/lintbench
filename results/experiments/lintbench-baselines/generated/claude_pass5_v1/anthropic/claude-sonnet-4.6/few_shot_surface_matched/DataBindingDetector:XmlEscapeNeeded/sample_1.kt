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

        // Check for characters that need XML escaping
        for (i in value.indices) {
            val c = value[i]
            when (c) {
                '<' -> {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Attribute value contains `<` which must be escaped as `&lt;` in XML"
                    )
                    return
                }
                '&' -> {
                    // Check if it's already a valid entity reference or character reference
                    if (!isValidEntityReference(value, i)) {
                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Attribute value contains `&` which must be escaped as `&amp;` in XML"
                        )
                        return
                    }
                }
                '"' -> {
                    // Double quotes are not allowed in attribute values delimited by double quotes
                    // (the XML parser would have already caught truly unescaped ones, but
                    // we flag any literal unescaped double quote in the raw value)
                    val rawText = attribute.ownerDocument?.let {
                        try {
                            context.getContents()?.let { contents ->
                                val location = context.getValueLocation(attribute)
                                val start = location.start
                                if (start != null) {
                                    val offset = start.offset
                                    if (offset >= 0 && offset < contents.length) {
                                        // Just use the parsed value for detection
                                        null
                                    } else null
                                } else null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    // The XML parser normalizes attribute values, so a literal `"` in the
                    // parsed value indicates it was escaped as `&quot;` or the attribute used
                    // single-quote delimiters. We only need to warn about `<` and `&`.
                    _ -> { /* no-op */ }
                }
            }
        }
    }

    /**
     * Returns true if the `&` at position [ampIndex] in [value] is the start of a valid
     * XML entity reference (e.g. `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`, or a
     * numeric character reference like `&#123;` or `&#x1A;`).
     */
    private fun isValidEntityReference(value: String, ampIndex: Int): Boolean {
        val rest = value.substring(ampIndex + 1)
        val semicolonIndex = rest.indexOf(';')
        if (semicolonIndex <= 0) return false

        val entity = rest.substring(0, semicolonIndex)

        // Named entities allowed in XML
        if (entity == "amp" || entity == "lt" || entity == "gt" ||
            entity == "quot" || entity == "apos"
        ) {
            return true
        }

        // Numeric character references: &#digits; or &#xhex;
        if (entity.startsWith("#")) {
            val ref = entity.substring(1)
            return if (ref.startsWith("x") || ref.startsWith("X")) {
                ref.length > 1 && ref.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            } else {
                ref.isNotEmpty() && ref.all { it.isDigit() }
            }
        }

        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation =
                "When a string contains characters that have special usage in XML, " +
                    "you must escape the characters. For example, if your attribute value " +
                    "contains a `<` character it must be escaped as `&lt;`, and if it " +
                    "contains an `&` character it must be escaped as `&amp;`.",
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
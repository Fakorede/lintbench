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
        if (value.isEmpty()) return

        // Check for unescaped characters that have special meaning in XML
        for (i in value.indices) {
            val ch = value[i]
            when (ch) {
                '<' -> {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Unescaped `<` character; replace with `&lt;`"
                    )
                    return
                }
                '>' -> {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Unescaped `>` character; replace with `&gt;`"
                    )
                    return
                }
                '&' -> {
                    // Allow valid XML entity references: &amp; &lt; &gt; &apos; &quot; &#...; &#x...;
                    val rest = value.substring(i + 1)
                    if (!rest.startsWith("amp;") &&
                        !rest.startsWith("lt;") &&
                        !rest.startsWith("gt;") &&
                        !rest.startsWith("apos;") &&
                        !rest.startsWith("quot;") &&
                        !isNumericEntityReference(rest)
                    ) {
                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Unescaped `&` character; replace with `&amp;`"
                        )
                        return
                    }
                }
                '"' -> {
                    // Double quotes inside an attribute value delimited by double quotes must be escaped
                    val rawValue = attribute.ownerDocument?.let {
                        // The parsed value has already been unescaped by the XML parser,
                        // so we need to check the raw XML text instead.
                        null
                    }
                    // The XML parser will have already rejected truly unescaped quotes,
                    // but in lint's context we can flag this if we see it in the value.
                    // We skip this case as the parser itself would have caught it.
                    _ = rawValue
                }
                else -> { /* no issue */ }
            }
        }
    }

    companion object {
        private fun isNumericEntityReference(rest: String): Boolean {
            if (rest.startsWith("#")) {
                val semicolonIndex = rest.indexOf(';')
                if (semicolonIndex > 1) {
                    val digits = rest.substring(1, semicolonIndex)
                    if (digits.startsWith("x") || digits.startsWith("X")) {
                        val hex = digits.substring(1)
                        return hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                    }
                    return digits.isNotEmpty() && digits.all { it.isDigit() }
                }
            }
            return false
        }

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation =
                "When a string contains characters that have special usage in XML, " +
                "you must escape the characters. For example, if you have a `<` character " +
                "in a string, you must escape it as `&lt;`. Similarly, `&` must be escaped " +
                "as `&amp;`.",
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
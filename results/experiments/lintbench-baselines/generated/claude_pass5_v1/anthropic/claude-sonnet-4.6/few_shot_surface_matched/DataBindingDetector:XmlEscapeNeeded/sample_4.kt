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
                    // Check if it's already an escape sequence (e.g. &amp;, &lt;, &gt;, &quot;, &apos;, &#...)
                    val rest = value.substring(i + 1)
                    if (!rest.startsWith("amp;") &&
                        !rest.startsWith("lt;") &&
                        !rest.startsWith("gt;") &&
                        !rest.startsWith("quot;") &&
                        !rest.startsWith("apos;") &&
                        !rest.startsWith("#")
                    ) {
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
                    // Double quotes inside an attribute value delimited by double quotes need escaping
                    val rawText = attribute.ownerDocument?.let {
                        try {
                            context.getContents()?.let { contents ->
                                val location = context.getValueLocation(attribute)
                                val start = location.start
                                if (start != null) {
                                    val offset = start.offset
                                    if (offset > 0 && offset < contents.length) {
                                        val delimiter = contents[offset - 1]
                                        delimiter
                                    } else null
                                } else null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    // If delimiter is double-quote, a literal double-quote in value needs escaping
                    if (rawText == '"') {
                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "Attribute value contains `\"` which must be escaped as `&quot;` in XML"
                        )
                        return
                    }
                }
                else -> { /* no issue */ }
            }
        }
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
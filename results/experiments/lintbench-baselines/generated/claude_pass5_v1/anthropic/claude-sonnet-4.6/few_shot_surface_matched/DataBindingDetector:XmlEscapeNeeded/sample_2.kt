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
                    // Allow already-escaped sequences: &amp; &lt; &gt; &quot; &apos; &#...; &name;
                    val rest = value.substring(i + 1)
                    val semicolonIndex = rest.indexOf(';')
                    if (semicolonIndex > 0) {
                        val entity = rest.substring(0, semicolonIndex)
                        // Valid named entities or numeric references
                        if (entity == "amp" || entity == "lt" || entity == "gt" ||
                            entity == "quot" || entity == "apos" ||
                            entity.startsWith("#")
                        ) {
                            // This & is already part of a valid escape sequence, skip
                            continue
                        }
                    }
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Attribute value contains `&` which must be escaped as `&amp;` in XML"
                    )
                    return
                }
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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class DataBindingDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith("@{") && !value.startsWith("@={")) {
            return
        }

        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (start < 0 || end > contents.length || start > end) return
        val rawValue = contents.substring(start, end)

        var i = 0
        while (i < rawValue.length) {
            val c = rawValue[i]
            if (c == '<') {
                context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Must escape '<' as '&lt;'"
                )
                return
            } else if (c == '&') {
                val remaining = rawValue.substring(i)
                val match = ENTITY_PATTERN.find(remaining)
                if (match == null || match.range.start != 0) {
                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "Must escape '&' as '&amp;'"
                    )
                    return
                } else {
                    i += match.value.length
                    continue
                }
            }
            i++
        }
    }

    companion object {
        private val ENTITY_PATTERN = Regex("^&(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);")

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.LAYOUT_SCOPE
            )
        )
    }
}
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

    override fun getApplicableAttributes(): Collection<String> {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value ?: return

        if (!value.startsWith("@{") && !value.startsWith("={")) {
            return
        }

        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (start < 0 || end > contents.length || start >= end) {
            return
        }

        val rawValue = contents.substring(start, end)

        val hasLessThan = rawValue.contains("<")
        val hasUnescapedAmp = UNESCAPED_AMPERSAND.containsMatchIn(rawValue)

        if (hasLessThan || hasUnescapedAmp) {
            val message = "Characters such as '<' and '&' must be escaped in XML/DataBinding expressions (use '&lt;' and '&amp;')"
            context.report(
                ISSUE,
                attribute,
                location,
                message
            )
        }
    }

    companion object {
        private val UNESCAPED_AMPERSAND = Regex("&(?![a-zA-Z0-9#]+;)")

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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
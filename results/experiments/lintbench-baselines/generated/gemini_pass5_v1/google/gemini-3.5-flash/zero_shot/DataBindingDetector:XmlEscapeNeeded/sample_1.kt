package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import java.util.regex.Pattern

class DataBindingDetector : LayoutDetector() {

    companion object {
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
                Scope.LAYOUT_RESOURCE_FILES
            )
        )

        private val UNESCAPED_AMP = Pattern.compile("&(?![a-zA-Z0-9#]+;)")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.contains("@{") && !value.contains("@={")) {
            return
        }

        val start = context.getValueStartOffset(attribute)
        val end = context.getValueEndOffset(attribute)
        if (start < 0 || end < 0 || start >= end) {
            return
        }

        val source = context.getContents() ?: return
        if (end > source.length) {
            return
        }
        val rawValue = source.subSequence(start, end).toString()

        if (rawValue.contains("<")) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Character '<' must be escaped as '&lt;'"
            )
            return
        }

        val matcher = UNESCAPED_AMP.matcher(rawValue)
        if (matcher.find()) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Character '&' must be escaped as '&amp;'"
            )
        }
    }
}
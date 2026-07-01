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
        return ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (end > contents.length || start < 0 || start > end) return
        val source = contents.substring(start, end)

        val entityRegex = Regex("&(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);", RegexOption.IGNORE_CASE)
        val cleanSource = source.replace(entityRegex, "")

        if (cleanSource.contains('&') || cleanSource.contains('<')) {
            context.report(
                ISSUE,
                attribute,
                location,
                "When a string contains characters that have special usage in XML, you must escape the characters"
            )
        }
    }

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
                Scope.LAYOUT_SCOPE
            )
        )
    }
}
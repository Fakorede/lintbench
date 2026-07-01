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
        return listOf("*")
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value ?: return
        if (UNESCAPED_XML_CHARS.containsMatchIn(value)) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Missing XML escape: Use &lt; instead of < and &amp; instead of & in XML attributes"
            )
        }
    }

    companion object {
        private val UNESCAPED_XML_CHARS = Regex("<|&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[\\da-fA-F]+;)")

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
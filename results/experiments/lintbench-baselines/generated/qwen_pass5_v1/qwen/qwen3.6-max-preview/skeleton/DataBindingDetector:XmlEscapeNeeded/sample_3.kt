package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML (such as <, >, &, \", or '), you must escape them using standard XML entities (e.g., &lt;, &gt;, &amp;, &quot;, &apos;) to prevent parsing errors.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("*")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val contents = context.getContents() ?: return
        val location = context.getLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        if (end <= start) return

        val rawText = contents.substring(start, end)

        // Detect unescaped < or & that are not part of valid XML entities
        val unescapedPattern = Regex("[<]|&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)")
        if (unescapedPattern.containsMatchIn(rawText)) {
            context.report(
                ISSUE,
                attribute,
                location,
                "XML special characters must be escaped (e.g., use &lt; for < and &amp; for &)"
            )
        }
    }
}
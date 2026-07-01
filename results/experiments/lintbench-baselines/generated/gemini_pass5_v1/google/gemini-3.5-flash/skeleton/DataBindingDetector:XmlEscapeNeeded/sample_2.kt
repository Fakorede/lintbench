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

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val UNESCAPED_XML_CHARACTERS = Regex("[<]|&(?!(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)")

        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters. For example, use `&lt;` instead of `<` and `&amp;&amp;` instead of `&&` in data binding expressions.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.contains("@{") && !value.contains("@={")) return

        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (start < 0 || end > contents.length || start > end) return
        val rawValue = contents.substring(start, end)

        if (UNESCAPED_XML_CHARACTERS.containsMatchIn(rawValue)) {
            context.report(
                ISSUE,
                attribute,
                location,
                "Characters such as '<' and '&' must be escaped in XML attributes (use '&lt;' and '&amp;')"
            )
        }
    }
}
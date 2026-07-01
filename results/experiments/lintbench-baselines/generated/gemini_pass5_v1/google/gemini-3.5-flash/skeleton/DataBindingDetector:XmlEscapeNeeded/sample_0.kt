package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

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
            explanation = "When using data binding expressions in layout files, characters that have special meaning in XML (such as `<` and `&`) must be escaped. For example, use `&lt;` instead of `<` and `&amp;&amp;` instead of `&&`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val ENTITY_REGEX = Regex("^&(amp|lt|gt|apos|quot|#[0-9]+|#x[0-9a-fA-F]+);")
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value ?: return
        val isDataBinding = (value.startsWith("@{") || value.startsWith("@={")) && value.endsWith("}")
        if (!isDataBinding) return

        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (start < 0 || end > contents.length || start > end) return

        val rawValue = contents.substring(start, end)

        var hasError = false
        var message = ""

        if (rawValue.contains("<")) {
            hasError = true
            message = "Must escape `<` as `&lt;` in data binding expressions"
        } else if (hasUnescapedAmpersand(rawValue)) {
            hasError = true
            message = "Must escape `&` as `&amp;` in data binding expressions"
        }

        if (hasError) {
            context.report(
                issue = ISSUE,
                scope = attribute,
                location = location,
                message = message
            )
        }
    }

    private fun hasUnescapedAmpersand(s: String): Boolean {
        var index = s.indexOf('&')
        while (index != -1) {
            val remainder = s.substring(index)
            val match = ENTITY_REGEX.find(remainder)
            if (match == null || match.range.start != 0) {
                return true
            }
            index = s.indexOf('&', index + match.value.length)
        }
        return false
    }
}
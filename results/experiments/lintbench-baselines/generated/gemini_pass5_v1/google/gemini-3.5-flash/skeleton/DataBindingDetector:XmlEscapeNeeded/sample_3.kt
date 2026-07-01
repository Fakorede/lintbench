package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private const val ENTITY_REGEX = """&(amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);"""

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When using data binding expressions in layout files, characters like `<` and `&` have special meaning in XML and must be escaped (e.g. use `&lt;` instead of `<` and `&amp;` instead of `&`).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        val contents = context.getContents() ?: return
        if (start in 0..contents.length && end in 0..contents.length && start < end) {
            val rawValue = contents.substring(start, end)
            val valueWithoutQuotes = if ((rawValue.startsWith("\"") && rawValue.endsWith("\"")) ||
                                         (rawValue.startsWith("'") && rawValue.endsWith("'"))) {
                rawValue.substring(1, rawValue.length - 1)
            } else {
                rawValue
            }
            val isDataBinding = (valueWithoutQuotes.startsWith("@{") || valueWithoutQuotes.startsWith("@={")) && valueWithoutQuotes.endsWith("}")
            if (isDataBinding) {
                val clean = valueWithoutQuotes.replace(ENTITY_REGEX.toRegex(), "_")
                if (clean.contains("<") || clean.contains("&")) {
                    context.report(
                        ISSUE,
                        attribute,
                        location,
                        "Characters like '<' and '&' must be escaped in XML/DataBinding expressions"
                    )
                }
            }
        }
    }
}
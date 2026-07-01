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
            explanation = "XML reserves certain characters for markup. Characters such as `<` and `&` must be escaped as `&lt;` and `&amp;` respectively when used in attribute values or text content. Failing to escape these characters will result in XML parsing errors.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val XML_ENTITY_REGEX = Regex("&(lt|gt|amp|quot|apos|#\\d+|#x[0-9a-fA-F]+);")
    }

    override fun getApplicableAttributes(): Collection<String>? = listOf("*")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        // Remove valid XML entities to isolate unescaped special characters
        val sanitized = value.replace(XML_ENTITY_REGEX, "")

        if ('<' in sanitized) {
            context.report(
                ISSUE,
                context.getValueLocation(attribute),
                "Replace `<` with `&lt;`"
            )
        } else if ('&' in sanitized) {
            context.report(
                ISSUE,
                context.getValueLocation(attribute),
                "Replace `&` with `&amp;`"
            )
        }
    }
}
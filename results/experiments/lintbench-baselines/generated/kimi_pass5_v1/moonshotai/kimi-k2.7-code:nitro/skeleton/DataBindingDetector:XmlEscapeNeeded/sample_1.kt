package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
            explanation = """
                XML reserves certain characters for markup. If an attribute value
                contains characters such as `<`, `>`, `&`, `"`, or `'`, they must be
                escaped using entities (for example `&lt;`, `&gt;`, `&amp;`,
                `&quot;`, `&apos;`). Unescaped characters can break the XML
                document and lead to build or runtime errors.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value ?: return
        if (requiresXmlEscape(value)) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The string contains characters that must be escaped in XML.",
            )
        }
    }

    private fun requiresXmlEscape(value: String): Boolean {
        for (c in value) {
            when (c) {
                '<', '>', '&', '\'', '"' -> return true
            }
        }
        return false
    }
}
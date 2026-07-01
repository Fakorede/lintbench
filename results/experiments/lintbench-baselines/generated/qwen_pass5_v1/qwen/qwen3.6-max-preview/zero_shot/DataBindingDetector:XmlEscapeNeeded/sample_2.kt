package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Text
import java.util.regex.Pattern

class DataBindingDetector : Detector(), Detector.XmlScanner {

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
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val UNESCAPED_XML_PATTERN = Pattern.compile("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)|<")
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkValue(context, attribute, attribute.value)
    }

    override fun visitText(context: XmlContext, text: Text) {
        checkValue(context, text, text.data)
    }

    private fun checkValue(context: XmlContext, node: org.w3c.dom.Node, value: String) {
        val matcher = UNESCAPED_XML_PATTERN.matcher(value)
        while (matcher.find()) {
            val start = matcher.start()
            val matched = matcher.group()
            val replacement = if (matched == "<") "&lt;" else "&amp;"
            val message = "Unescaped XML character '$matched'. Use `$replacement` instead."
            context.report(
                ISSUE,
                node,
                context.getLocation(node, start, start + matched.length),
                message
            )
        }
    }
}
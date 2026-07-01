package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val UNESCAPED_XML_CHARS = Regex("[<>&]")
    }

    override fun getIssues(): List<Issue> = listOf(ISSUE)

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value
            if (value != null && UNESCAPED_XML_CHARS.containsMatchIn(value)) {
                context.report(
                    ISSUE,
                    context.getValueLocation(attr),
                    "Missing XML Escape: Attribute value contains unescaped XML special characters"
                )
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                val text = node.nodeValue
                if (text != null && UNESCAPED_XML_CHARS.containsMatchIn(text)) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Missing XML Escape: Text content contains unescaped XML special characters"
                    )
                }
            }
        }
    }
}
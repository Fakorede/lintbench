package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                val text = node.nodeValue
                if (!text.isNullOrEmpty() && (text.contains('<') || text.contains('&'))) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Missing XML escape for special character"
                    )
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (!value.isNullOrEmpty() && (value.contains('<') || value.contains('&'))) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Missing XML escape for special character"
            )
        }
    }
}
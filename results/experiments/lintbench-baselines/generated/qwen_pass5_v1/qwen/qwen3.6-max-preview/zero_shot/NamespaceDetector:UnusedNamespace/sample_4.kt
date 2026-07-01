package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val declaredNamespaces = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        val allElements = document.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val element = allElements.item(i) as Element
            val attrs = element.attributes

            for (j in 0 until attrs.length) {
                val attr = attrs.item(j) as Attr
                val name = attr.name
                if (name.startsWith("xmlns:")) {
                    val prefix = name.substring(6)
                    if (prefix != "xml" && prefix != "xmlns") {
                        declaredNamespaces[prefix] = attr
                    }
                }
            }

            val tagName = element.tagName
            val colonIndex = tagName.indexOf(':')
            if (colonIndex > 0) {
                usedPrefixes.add(tagName.substring(0, colonIndex))
            }

            for (j in 0 until attrs.length) {
                val attr = attrs.item(j) as Attr
                val attrName = attr.name
                val attrColon = attrName.indexOf(':')
                if (attrColon > 0 && !attrName.startsWith("xmlns:")) {
                    usedPrefixes.add(attrName.substring(0, attrColon))
                }
            }
        }

        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace `$prefix`"
                )
            }
        }
    }
}
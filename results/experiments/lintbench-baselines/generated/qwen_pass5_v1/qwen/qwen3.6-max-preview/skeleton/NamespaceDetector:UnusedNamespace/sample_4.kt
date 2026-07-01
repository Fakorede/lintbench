package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val elements = document.getElementsByTagName("*")
        val declaredNamespaces = mutableListOf<Pair<String, Attr>>()
        val usedPrefixes = mutableSetOf<String>()

        for (i in 0 until elements.length) {
            val element = elements.item(i) as Element
            val tagName = element.tagName
            val colonIndex = tagName.indexOf(':')
            if (colonIndex > 0) {
                usedPrefixes.add(tagName.substring(0, colonIndex))
            }

            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attr = attributes.item(j) as Attr
                val attrName = attr.name
                if (attrName.startsWith("xmlns:")) {
                    declaredNamespaces.add(attrName.substring(6) to attr)
                } else if (attrName == "xmlns") {
                    continue
                } else {
                    val attrColon = attrName.indexOf(':')
                    if (attrColon > 0) {
                        usedPrefixes.add(attrName.substring(0, attrColon))
                    }
                }
            }
        }

        for ((prefix, attr) in declaredNamespaces) {
            if (prefix.isNotEmpty() && prefix !in usedPrefixes && prefix != "xml" && prefix != "xmlns") {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace declaration `xmlns:$prefix`"
                )
            }
        }
    }
}
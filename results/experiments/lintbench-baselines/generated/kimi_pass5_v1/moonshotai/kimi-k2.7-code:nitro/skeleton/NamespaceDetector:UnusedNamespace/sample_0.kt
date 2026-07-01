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
            explanation = "Unused namespace declarations take up space and require processing that is not necessary. Removing them can reduce file size and improve parsing performance.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val declared = mutableListOf<Pair<String, Attr>>()
        val used = mutableSetOf<String>()

        val allElements = document.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val element = allElements.item(i) as? Element ?: continue

            val elementPrefix = element.prefix
            if (elementPrefix != null) {
                used.add(elementPrefix)
            }

            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attr = attributes.item(j) as Attr
                if (attr.prefix == "xmlns") {
                    val declaredPrefix = attr.localName ?: continue
                    declared.add(declaredPrefix to attr)
                } else {
                    val attrPrefix = attr.prefix
                    if (attrPrefix != null) {
                        used.add(attrPrefix)
                    }
                }
            }
        }

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace declaration: \"$prefix\"",
                )
            }
        }
    }
}
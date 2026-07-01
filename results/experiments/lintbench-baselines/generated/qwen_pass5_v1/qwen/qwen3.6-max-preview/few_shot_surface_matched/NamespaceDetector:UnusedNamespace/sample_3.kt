package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val usedPrefixes = mutableSetOf<String>()
        collectUsedPrefixes(root, usedPrefixes)

        val attributes = root.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring(6)
                if (prefix !in usedPrefixes) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Unused namespace `$prefix`"
                    )
                }
            }
        }
    }

    private fun collectUsedPrefixes(element: Element, used: MutableSet<String>) {
        element.prefix?.let { used.add(it) }
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            (attrs.item(i) as? Attr)?.prefix?.let { used.add(it) }
        }
        var child = element.firstElementChild
        while (child != null) {
            collectUsedPrefixes(child, used)
            child = child.nextElementSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val usedPrefixes = mutableSetOf<String>()
        val declaredAttrs = mutableListOf<Attr>()

        collectNamespaces(root, usedPrefixes, declaredAttrs)

        for (attr in declaredAttrs) {
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val prefix = name.substring(6)
                if (prefix !in usedPrefixes) {
                    context.report(ISSUE, context.getLocation(attr), "Unused namespace declaration")
                }
            }
        }
    }

    private fun collectNamespaces(element: Element, used: MutableSet<String>, declared: MutableList<Attr>) {
        element.prefix?.let { used.add(it) }
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            val nodeName = attr.nodeName
            if (nodeName.startsWith("xmlns:")) {
                declared.add(attr)
            } else {
                attr.prefix?.let { used.add(it) }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectNamespaces(child as Element, used, declared)
            }
        }
    }
}
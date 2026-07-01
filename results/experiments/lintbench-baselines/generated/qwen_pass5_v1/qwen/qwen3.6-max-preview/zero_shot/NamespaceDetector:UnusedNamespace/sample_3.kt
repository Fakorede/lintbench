package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class NamespaceDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE_UNUSED_NAMESPACE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val declaredNamespaces = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        collectNamespaces(root, declaredNamespaces, usedPrefixes)

        for ((prefix, attr) in declaredNamespaces) {
            if (prefix !in usedPrefixes) {
                context.report(
                    ISSUE_UNUSED_NAMESPACE,
                    context.getLocation(attr),
                    "Unused namespace $prefix"
                )
            }
        }
    }

    private fun collectNamespaces(
        element: Element,
        declared: MutableMap<String, Attr>,
        used: MutableSet<String>
    ) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                declared[name.substring(6)] = attr
            } else if (':' in name) {
                used.add(name.substringBefore(':'))
            }
        }

        val tagName = element.tagName
        if (':' in tagName) {
            used.add(tagName.substringBefore(':'))
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectNamespaces(child as Element, declared, used)
            }
            child = child.nextSibling
        }
    }
}
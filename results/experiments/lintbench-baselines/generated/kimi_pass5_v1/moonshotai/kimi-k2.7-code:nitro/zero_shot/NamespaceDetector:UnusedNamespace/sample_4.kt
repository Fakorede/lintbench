package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {
    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL
    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.prefix != "xmlns") continue

            val declaredPrefix = attr.localName ?: continue
            if (!isPrefixUsed(element, declaredPrefix)) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "The namespace \"$declaredPrefix\" is unused"
                )
            }
        }
    }

    private fun isPrefixUsed(root: Element, prefix: String): Boolean {
        if (root.prefix == prefix) return true

        val attributes = root.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.prefix == prefix) return true
        }

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && isPrefixUsed(node, prefix)) return true
        }

        return false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                Consider removing unused namespace declarations.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
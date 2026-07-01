package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode !is Document) {
            return
        }

        val declared = mutableMapOf<String, Attr>()
        val used = mutableSetOf<String>()

        collectNamespaces(element, declared, used)

        for ((prefix, attr) in declared) {
            if (prefix !in used) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "The `$prefix` namespace is declared but never used"
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
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val prefix = name.substringAfter("xmlns:")
                if (prefix != "xml" && prefix != "xmlns") {
                    declared[prefix] = attr
                }
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
            if (child is Element) {
                collectNamespaces(child, declared, used)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
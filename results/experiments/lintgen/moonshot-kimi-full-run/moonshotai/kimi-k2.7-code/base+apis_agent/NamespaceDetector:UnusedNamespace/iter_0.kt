package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.EnumSet

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val declarations = mutableListOf<Pair<String, Attr>>()
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                declarations.add(name.substringAfter(":") to attr)
            }
        }

        for ((prefix, attr) in declarations) {
            if (!isPrefixUsed(element, prefix)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace `$prefix`"
                )
            }
        }
    }

    private fun isPrefixUsed(element: Element, prefix: String): Boolean {
        if (element.prefix == prefix) {
            return true
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.name.startsWith("xmlns:")) continue
            if (attr.prefix == prefix) {
                return true
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element && !redeclaresPrefix(child, prefix)) {
                if (isPrefixUsed(child, prefix)) {
                    return true
                }
            }
            child = child.nextSibling
        }

        return false
    }

    private fun redeclaresPrefix(element: Element, prefix: String): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.name == "xmlns:$prefix") {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                Remove namespace declarations that are not used by any element or attribute in their scope.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE, Scope.MANIFEST)
            )
        )
    }
}
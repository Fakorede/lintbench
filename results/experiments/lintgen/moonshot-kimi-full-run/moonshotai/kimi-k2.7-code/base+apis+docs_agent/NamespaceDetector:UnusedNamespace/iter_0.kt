package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class NamespaceDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext) {
        val root = context.document.documentElement ?: return

        val declaredPrefixes = mutableMapOf<String, MutableList<Attr>>()
        val defaultDeclarations = mutableListOf<Attr>()
        val usedPrefixes = mutableSetOf<String>()
        var defaultNamespaceUsed = false

        fun visit(element: Element) {
            val attrs = element.attributes

            // Record namespace declarations first so the same element/attributes can use them.
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as? Attr ?: continue
                when {
                    attr.name == "xmlns" -> defaultDeclarations.add(attr)
                    attr.name.startsWith("xmlns:") -> {
                        val prefix = attr.name.substringAfter("xmlns:")
                        declaredPrefixes.getOrPut(prefix) { mutableListOf() }.add(attr)
                    }
                }
            }

            // Element name usage
            val elementPrefix = element.prefix?.takeIf { it.isNotEmpty() }
            if (elementPrefix != null) {
                usedPrefixes.add(elementPrefix)
            } else if (!element.namespaceURI.isNullOrEmpty()) {
                defaultNamespaceUsed = true
            }

            // Attribute usage (excluding namespace declaration attributes)
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i) as? Attr ?: continue
                if (attr.name == "xmlns" || attr.name.startsWith("xmlns:")) continue

                val attrPrefix = attr.prefix?.takeIf { it.isNotEmpty() }
                if (attrPrefix != null) {
                    usedPrefixes.add(attrPrefix)
                }
            }

            // Recurse into children
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    visit(child as Element)
                }
                child = child.nextSibling
            }
        }

        visit(root)

        for ((prefix, declarations) in declaredPrefixes) {
            if (prefix !in usedPrefixes) {
                for (attr in declarations) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Unused namespace declaration: `$prefix`"
                    )
                }
            }
        }

        if (!defaultNamespaceUsed) {
            for (attr in defaultDeclarations) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Unused namespace declaration: default namespace"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE, Scope.MANIFEST)
            )
        )
    }
}
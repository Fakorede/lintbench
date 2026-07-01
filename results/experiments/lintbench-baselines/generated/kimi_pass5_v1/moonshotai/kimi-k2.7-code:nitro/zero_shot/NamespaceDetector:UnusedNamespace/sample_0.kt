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
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val active = mutableMapOf<String, ArrayDeque<NsDecl>>()
        val defaultStack = ArrayDeque<NsDecl>()
        visitElement(context, root, active, defaultStack)
    }

    private fun visitElement(
        context: XmlContext,
        element: Element,
        active: MutableMap<String, ArrayDeque<NsDecl>>,
        defaultStack: ArrayDeque<NsDecl>
    ) {
        val pushed = mutableListOf<NsDecl>()
        val attributes = element.attributes

        // First, activate all namespace declarations on this element.
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            when {
                attr.name == "xmlns" -> {
                    val decl = NsDecl(attr, null)
                    defaultStack.addLast(decl)
                    pushed.add(decl)
                }
                attr.name.startsWith("xmlns:") -> {
                    val prefix = attr.name.substringAfter("xmlns:")
                    val decl = NsDecl(attr, prefix)
                    active.getOrPut(prefix) { ArrayDeque() }.addLast(decl)
                    pushed.add(decl)
                }
            }
        }

        // The element tag itself may use an active namespace.
        val elementPrefix = element.prefix
        if (elementPrefix.isNullOrEmpty()) {
            defaultStack.lastOrNull()?.used = true
        } else {
            active[elementPrefix]?.lastOrNull()?.used = true
        }

        // Non-declaration attributes may use an active namespace.
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name == "xmlns" || name.startsWith("xmlns:")) continue

            val attrPrefix = attr.prefix
            if (!attrPrefix.isNullOrEmpty()) {
                active[attrPrefix]?.lastOrNull()?.used = true
            }
        }

        // Recurse into children.
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                visitElement(context, child as Element, active, defaultStack)
            }
        }

        // Pop declarations introduced by this element and report unused ones.
        for (decl in pushed.asReversed()) {
            when {
                decl.prefix == null -> {
                    defaultStack.removeLast()
                    if (!decl.used) {
                        reportUnused(context, decl.attr, null)
                    }
                }
                else -> {
                    active[decl.prefix]!!.removeLast()
                    if (!decl.used) {
                        reportUnused(context, decl.attr, decl.prefix)
                    }
                }
            }
        }
    }

    private fun reportUnused(context: XmlContext, attr: Attr, prefix: String?) {
        val message = if (prefix != null) {
            "Unused namespace \"$prefix\""
        } else {
            "Unused default namespace"
        }
        context.report(ISSUE, context.getLocation(attr), message)
    }

    private data class NsDecl(
        val attr: Attr,
        val prefix: String?,
        var used: Boolean = false
    )

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                Remove any namespace declarations that are not used by elements or attributes in their scope.
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
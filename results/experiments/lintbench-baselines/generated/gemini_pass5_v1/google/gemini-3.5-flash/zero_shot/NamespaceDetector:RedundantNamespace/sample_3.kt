package com.android.tools.lint.checks

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

class NamespaceDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root, isRoot = true)
    }

    private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
        if (!isRoot) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                val name = attr.name
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    val location = context.getLocation(attr)
                    val fix = fix()
                        .name("Remove namespace declaration")
                        .replace()
                        .all()
                        .with("")
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE,
                        attr,
                        location,
                        "Redundant namespace declaration",
                        fix
                    )
                }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElement(context, child, false)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "RedundantNamespace",
            "Redundant namespace",
            "In Android XML documents, only specify the namespace on the root/document " +
                    "element. Namespace declarations elsewhere in the document are typically " +
                    "accidental leftovers from copy/pasting XML from other files or documentation.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
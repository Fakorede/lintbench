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
        private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = "In Android XML documents, only specify the namespace on the root/document element. " +
                "Namespace declarations elsewhere in the document are typically accidental leftovers from " +
                "copy/pasting XML from other files or documentation.",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val allElements = document.getElementsByTagName("*")

        for (i in 0 until allElements.length) {
            val element = allElements.item(i) as? Element ?: continue
            if (element === root) continue

            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attr = attributes.item(j) as? Attr ?: continue
                if (isNamespaceDeclaration(attr)) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Redundant namespace declaration `${attr.name}`",
                    )
                }
            }
        }
    }

    private fun isNamespaceDeclaration(attr: Attr): Boolean {
        return attr.namespaceURI == XMLNS_URI ||
            attr.name == "xmlns" ||
            attr.prefix == "xmlns"
    }
}
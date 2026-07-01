package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = "In Android XML documents, only specify the namespace on the root/document " +
                "element. Namespace declarations elsewhere in the document are typically " +
                "accidental leftovers from copy/pasting XML from other files or documentation.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (element === element.ownerDocument.documentElement) {
            return
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (isNamespaceDeclaration(attr)) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Redundant namespace declaration"
                )
            }
        }
    }

    private fun isNamespaceDeclaration(attr: Attr): Boolean {
        val name = attr.name
        return name == "xmlns" || name.startsWith("xmlns:") ||
            attr.namespaceURI == "http://www.w3.org/2000/xmlns/"
    }
}
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

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (element === context.document?.documentElement) {
            return
        }

        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val node = attributes.item(i)
            if (node is Attr) {
                val name = node.name
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Redundant namespace declaration"
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = "In Android XML documents, only specify the namespace on the root/document element. " +
                "Namespace declarations elsewhere in the document are typically accidental leftovers from " +
                "copy/pasting XML from other files or documentation.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
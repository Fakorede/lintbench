package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val root = element.ownerDocument?.documentElement ?: return
        if (element == root) {
            return
        }

        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) ?: continue
            val name = attr.nodeName
            if (name.startsWith(XMLNS_PREFIX) &&
                (name.length == XMLNS_PREFIX.length || name[XMLNS_PREFIX.length] == ':')
            ) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Redundant namespace"
                )
            }
        }
    }

    companion object {
        private const val XMLNS_PREFIX = "xmlns"

        @JvmField
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = "In Android XML documents, only specify the namespace on the root/document element. Namespace declarations elsewhere in the document are typically accidental leftovers from copy/pasting XML from other files or documentation.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
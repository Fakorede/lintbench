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

class NamespaceDetector : Detector(), XmlScanner {
    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (element === context.document.documentElement) {
            return
        }

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as Attr
            if (attr.name.startsWith(XMLNS_PREFIX) || attr.name == XMLNS) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Redundant namespace declaration: only the root element should declare a namespace"
                )
            }
        }
    }

    companion object {
        private const val XMLNS = "xmlns"
        private const val XMLNS_PREFIX = "xmlns:"

        @JvmField
        val ISSUE: Issue = Issue.create(
            "RedundantNamespace",
            "Redundant namespace",
            "In Android XML documents, only specify the namespace on the root/document element. "
                + "Namespace declarations elsewhere in the document are typically accidental leftovers "
                + "from copy/pasting XML from other files or documentation.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE, Scope.MANIFEST_SCOPE)
        )
    }
}
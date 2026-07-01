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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "RedundantNamespace",
            briefDescription = "Redundant namespace",
            explanation = """
                In Android XML documents, only specify the namespace on the root/document element. \
                Namespace declarations elsewhere in the document are typically accidental leftovers from copy/pasting XML from other files or documentation.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (element == element.ownerDocument?.documentElement) {
            return
        }

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            if (attr.name.startsWith("xmlns:") || attr.name == "xmlns") {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "Redundant namespace declaration"
                )
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): List<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (element == context.document.documentElement) {
            return
        }

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (name.startsWith("xmlns:") || name == "xmlns") {
                val location = context.getLocation(attr)
                val message = "Redundant namespace declaration on a non-root element"
                context.report(ISSUE, attr, location, message)
            }
        }
    }

    companion object {
        private const val ID = "RedundantNamespace"

        val ISSUE = Issue.create(
            id = ID,
            briefDescription = "Redundant namespace declaration",
            explanation = """
                In Android XML documents, namespace declarations are only needed on the root element.
                Declaring a namespace on a child element is usually an accidental leftover from
                copy/pasting XML from other files or documentation and should be removed.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
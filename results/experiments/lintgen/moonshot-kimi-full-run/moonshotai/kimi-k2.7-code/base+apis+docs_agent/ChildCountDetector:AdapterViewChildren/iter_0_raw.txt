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
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> =
        listOf(XmlScannerConstants.ALL)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!hasElementChild(element)) {
            return
        }

        val cls = context.evaluator.findLayoutClass(element) ?: return
        if (context.evaluator.extendsClass(cls, ADAPTER_VIEW, false)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "AdapterView cannot have children in XML"
            )
        }
    }

    private fun hasElementChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "AdapterViewChildren",
            "AdapterView cannot have children in XML",
            """
                An `AdapterView` such as a `ListView` must be configured with an adapter in code.
                It cannot contain child views in XML.
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val ADAPTER_VIEW = "android.widget.AdapterView"
    }
}
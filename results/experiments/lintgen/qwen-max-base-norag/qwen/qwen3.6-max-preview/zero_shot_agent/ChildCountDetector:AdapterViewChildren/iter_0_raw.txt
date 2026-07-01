package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.SdkConstants
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from Java code, \
                such as a `ListAdapter`. Defining children in XML is not supported and will cause \
                a runtime exception.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            SdkConstants.ADAPTER_VIEW,
            SdkConstants.LIST_VIEW,
            SdkConstants.GRID_VIEW,
            SdkConstants.SPINNER,
            SdkConstants.EXPANDABLE_LIST_VIEW,
            SdkConstants.STACK_VIEW,
            SdkConstants.GALLERY
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "`AdapterView` cannot have children in XML"
                )
                return
            }
            child = child.nextSibling
        }
    }
}
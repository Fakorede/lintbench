package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            SdkConstants.LIST_VIEW,
            SdkConstants.EXPANDABLE_LIST_VIEW,
            SdkConstants.GRID_VIEW,
            SdkConstants.SPINNER,
            SdkConstants.GALLERY,
            SdkConstants.ADAPTER_VIEW_FLIPPER,
            SdkConstants.ADAPTER_VIEW_ANIMATOR
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "AdapterView cannot have children in XML"
                )
                return
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                `AdapterView` subclasses such as `ListView`, `GridView`, `Spinner` and `Gallery` \
                must be configured with an Adapter from code, and as such should not have any \
                children in XML layout files.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
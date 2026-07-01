package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            SdkConstants.LIST_VIEW,
            SdkConstants.GRID_VIEW,
            SdkConstants.SPINNER,
            SdkConstants.GALLERY,
            SdkConstants.EXPANDABLE_LIST_VIEW,
            SdkConstants.STACK_VIEW
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "An AdapterView cannot have children in XML"
                )
                return
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "AdapterViewChildren",
            "`AdapterView` cannot have children in XML",
            "An `AdapterView` such as a `ListView` must be configured with data from " +
                    "Java code, such as a `ListAdapter`.\n\n" +
                    "Reference: https://developer.android.com/reference/android/widget/AdapterView.html",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
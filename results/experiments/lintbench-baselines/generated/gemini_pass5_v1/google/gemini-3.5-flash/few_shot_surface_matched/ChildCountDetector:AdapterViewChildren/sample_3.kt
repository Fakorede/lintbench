package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewFlipper",
            "AdapterViewAnimator",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.StackView",
            "android.widget.AdapterViewFlipper",
            "android.widget.AdapterViewAnimator"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "An `AdapterView` such as a `ListView` cannot have children in XML"
                )
                break
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, such as a `ListAdapter`.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(ChildCountDetector::class.java, Scope.LAYOUT_SCOPE)
        )
    }
}
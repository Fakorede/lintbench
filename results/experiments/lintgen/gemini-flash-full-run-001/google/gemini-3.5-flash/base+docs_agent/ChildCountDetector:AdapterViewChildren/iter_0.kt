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

class ChildCountDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "ExpandableListView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.StackView",
            "android.widget.AdapterViewAnimator",
            "android.widget.AdapterViewFlipper",
            "android.widget.ExpandableListView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "An `AdapterView` cannot have children in XML"
                )
                break
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from Java code, \
                such as a `ListAdapter`. Therefore, you cannot add children to them in XML.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
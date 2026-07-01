package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ChildCountDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView`, `GridView` or `Spinner` is
                populated with data from code using an `Adapter` (for example a
                `ListAdapter`). It cannot contain child views declared in XML.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val ADAPTER_VIEWS = listOf(
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.ExpandableListView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.AdapterViewAnimator",
            "android.widget.AdapterViewFlipper",
            "android.widget.StackView"
        )
    }

    override fun getApplicableElements(): Collection<String>? = ADAPTER_VIEWS

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is org.w3c.dom.Element) {
                continue
            }
            if (child.tagName == "requestFocus") {
                continue
            }
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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "StackView",
            "AdapterViewFlipper",
            "AdapterViewAnimator",
            "AdapterView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.ExpandableListView",
            "android.widget.StackView",
            "android.widget.AdapterViewFlipper",
            "android.widget.AdapterViewAnimator",
            "android.widget.AdapterView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val tagName = child.tagName
                if (tagName != "requestFocus" && tagName != "tag") {
                    val parentName = element.tagName.substringAfterLast('.')
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "A $parentName cannot have helper views declared in layout files"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from Java code, \
                such as a `ListAdapter`.
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
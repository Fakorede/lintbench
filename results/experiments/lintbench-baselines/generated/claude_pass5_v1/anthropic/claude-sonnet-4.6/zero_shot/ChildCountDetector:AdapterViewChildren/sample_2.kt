package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks that AdapterViews do not have children declared in XML layout files.
 */
class ChildCountDetector : LayoutDetector() {

    companion object {
        /** The main issue discovered by this detector */
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from \
                Java code, such as a `ListAdapter`.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            moreInfo = "https://developer.android.com/reference/android/widget/AdapterView.html",
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        /** AdapterView subclasses that should not have children in XML */
        private val ADAPTER_VIEW_CLASSES = setOf(
            "AdapterView",
            "AbsListView",
            "AbsSpinner",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "AppWidgetHostView",
            "ExpandableListView",
            "Gallery",
            "GridView",
            "ListView",
            "Spinner",
            "StackView"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return ADAPTER_VIEW_CLASSES
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        var childElementCount = 0
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                childElementCount++
            }
        }

        if (childElementCount > 0) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(element),
                message = "A list/grid should have no children declared in XML"
            )
        }
    }
}
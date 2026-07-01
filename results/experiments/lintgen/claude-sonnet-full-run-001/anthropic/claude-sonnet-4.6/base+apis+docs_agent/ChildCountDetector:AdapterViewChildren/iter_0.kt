package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        private val ADAPTER_VIEW_CLASSES = setOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "AbsListView",
            "AbsSpinner",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "StackView"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from \
                Java code, such as a `ListAdapter`. Adding children to an `AdapterView` \
                in XML is not valid; the children will be ignored.
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
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_CLASSES

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
            val idAttr = element.getAttributeNS(ANDROID_URI, ATTR_ID)
            val viewName = element.localName ?: element.tagName
            val message = if (idAttr.isNotEmpty()) {
                "A $viewName should have no children declared in XML ($idAttr)"
            } else {
                "A $viewName should have no children declared in XML"
            }
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = message
            )
        }
    }
}
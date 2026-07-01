package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.SPINNER
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks that AdapterViews do not have children declared in XML.
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

        /** AdapterView subclasses that are commonly used in XML */
        private val ADAPTER_VIEW_CLASSES = setOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "ExpandableListView",
            "AbsListView",
            "AbsSpinner",
            "AbsAdapterView",
            "AutoCompleteTextView",
            "MultiAutoCompleteTextView",
            "StackView",
            "AdapterViewAnimator",
            "AdapterViewFlipper"
        )
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_CLASSES

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        var childCount = 0
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                childCount++
            }
        }

        if (childCount > 0) {
            val tagName = element.tagName
            // Spinner and AutoCompleteTextView are allowed to have children in some contexts,
            // but generally AdapterViews should not have children in XML.
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A `${tagName}` should have no children declared in XML: " +
                        "It should be populated dynamically using an `Adapter`"
            )
        }
    }
}
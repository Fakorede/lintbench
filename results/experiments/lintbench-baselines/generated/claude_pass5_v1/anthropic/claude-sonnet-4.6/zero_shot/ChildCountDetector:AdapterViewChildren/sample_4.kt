package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.CLASS_ADAPTERVIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterViews cannot have children",
            explanation = """
                `AdapterView` such as `ListView` must be configured with data from Java code, \
                such as a `ListAdapter`. They cannot have children declared in XML.
            """,
            moreInfo = "https://developer.android.com/reference/android/widget/AdapterView.html",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ADAPTER_VIEW_CLASSES = setOf(
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "AbsListView",
            "AbsSpinner",
            "ExpandableListView",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "StackView"
        )
    }

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_CLASSES

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        var hasElementChildren = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                hasElementChildren = true
                break
            }
        }

        if (hasElementChildren) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A `${element.localName}` should have no children declared in XML: " +
                        "should be populated instead from code"
            )
        }
    }

    private fun isAdapterView(element: Element, context: XmlContext): Boolean {
        val tagName = element.localName ?: element.nodeName
        if (tagName in ADAPTER_VIEW_CLASSES) {
            return true
        }

        // Check class attribute for custom views
        val classAttr = element.getAttributeNS(ANDROID_URI, ATTR_CLASS)
        if (classAttr.isNotEmpty()) {
            val simpleName = classAttr.substringAfterLast('.')
            if (simpleName in ADAPTER_VIEW_CLASSES) {
                return true
            }
        }

        return false
    }
}
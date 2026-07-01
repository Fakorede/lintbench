package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.VIEW_TAG
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

        /**
         * Returns true if the given tag name represents an AdapterView subclass.
         */
        private fun isAdapterView(tag: String): Boolean {
            // Handle fully qualified class names
            val simpleName = if (tag.contains('.')) {
                tag.substringAfterLast('.')
            } else {
                tag
            }
            return simpleName in ADAPTER_VIEW_CLASSES
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName

        // Determine the actual view class name
        val viewClass = if (tag == VIEW_TAG) {
            element.getAttributeNS(ANDROID_URI, ATTR_CLASS)
                .takeIf { it.isNotEmpty() }
                ?: element.getAttribute(ATTR_CLASS).takeIf { it.isNotEmpty() }
                ?: return
        } else {
            tag
        }

        if (!isAdapterView(viewClass)) {
            return
        }

        // Check if this AdapterView has any child elements
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
                issue = ISSUE,
                scope = element,
                location = context.getNameLocation(element),
                message = "A list/grid should have no children declared in XML"
            )
        }
    }
}
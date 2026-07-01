package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from \
                Java code, such as a `ListAdapter`.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (isAdapterView(tagName)) {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "A list view or spinner cannot have children in XML"
                    )
                    break
                }
            }
        }
    }

    private fun isAdapterView(tagName: String): Boolean {
        val baseName = if (tagName.contains('.')) {
            tagName.substring(tagName.lastIndexOf('.') + 1)
        } else {
            tagName
        }

        return when (baseName) {
            "AdapterView",
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewAnimator",
            "AdapterViewFlipper",
            "ExpandableListView" -> true
            else -> false
        }
    }
}
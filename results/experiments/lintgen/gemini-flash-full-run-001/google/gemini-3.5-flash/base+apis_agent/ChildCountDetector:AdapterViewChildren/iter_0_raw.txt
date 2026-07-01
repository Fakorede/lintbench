package com.android.tools.lint.checks

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

    override fun getApplicableElements(): Collection<String> {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (isAdapterView(tagName)) {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "A list view or spinner cannot have template children in XML"
                    )
                    break
                }
            }
        }
    }

    private fun isAdapterView(tagName: String): Boolean {
        val name = tagName.substringAfterLast('.')
        return name == "AdapterView" ||
                name == "ListView" ||
                name == "GridView" ||
                name == "Spinner" ||
                name == "Gallery" ||
                name == "StackView" ||
                name == "AdapterViewAnimator" ||
                name == "AdapterViewFlipper" ||
                name == "ExpandableListView"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data from Java code, \
                such as a `ListAdapter`.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.LAYOUT_RESOURCE_SCOPE
            )
        )
    }
}
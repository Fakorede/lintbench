package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isScrollView(element)) {
            return
        }

        var childCount = 0
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                childCount++
                if (childCount > 1) {
                    break
                }
            }
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "A ScrollView can only have one child widget. Wrap children in a container layout."
            )
        }
    }

    private fun isScrollView(element: Element): Boolean {
        val tagName = element.tagName
        return tagName == SCROLL_VIEW ||
            tagName == NESTED_SCROLL_VIEW ||
            tagName.endsWith(".$SCROLL_VIEW") ||
            tagName.endsWith(".$NESTED_SCROLL_VIEW")
    }

    companion object {
        private const val SCROLL_VIEW = "ScrollView"
        private const val NESTED_SCROLL_VIEW = "NestedScrollView"

        @JvmStatic
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation = """
                A ScrollView can only contain one direct child widget. If you need more children, \
                wrap them in a container layout such as a LinearLayout or ConstraintLayout.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
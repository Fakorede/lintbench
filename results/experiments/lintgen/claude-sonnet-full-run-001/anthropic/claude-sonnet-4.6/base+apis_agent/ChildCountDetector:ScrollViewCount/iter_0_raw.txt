package com.android.tools.lint.checks

import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ChildCountDetector : Detector(), XmlScanner {

    companion object {
        val SCROLLVIEW_ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation = """
                A `ScrollView` can only have one child widget. If you want more children, \
                wrap them in a container layout.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                childCount++
            }
        }

        if (childCount > 1) {
            context.report(
                SCROLLVIEW_ISSUE,
                element,
                context.getNameLocation(element),
                "`${element.tagName}` can have only one child"
            )
        }
    }
}
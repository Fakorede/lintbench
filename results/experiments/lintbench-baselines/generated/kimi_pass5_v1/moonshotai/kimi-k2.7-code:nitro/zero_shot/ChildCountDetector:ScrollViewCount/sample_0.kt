package com.android.tools.lint.checks

import com.android.SdkConstants
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

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_SCROLL_VIEW, SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                childCount++
            }
            if (childCount > 1) {
                break
            }
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(element),
                "A ScrollView can have only one child"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation = "A ScrollView can only have one child widget. If you want more children, wrap them in a container layout.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
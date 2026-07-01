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
        private const val ID = "ScrollViewCount"
        private const val BRIEF_DESCRIPTION = "ScrollView can have only one child"
        private const val EXPLANATION = "A `ScrollView` can only have one child widget. " +
                "If you want more children, wrap them in a container layout."

        val ISSUE = Issue.create(
            id = ID,
            briefDescription = BRIEF_DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("ScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                childCount++
            }
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                context.getLocation(element),
                BRIEF_DESCRIPTION
            )
        }
    }
}
package com.android.tools.lint.checks

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

    override fun getApplicableElements(): Collection<String> =
        listOf("ScrollView", "HorizontalScrollView")

    override fun visitElement(context: XmlContext, element: Element) {
        val childCount = (0 until element.childNodes.length).count {
            element.childNodes.item(it).nodeType == Node.ELEMENT_NODE
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A ScrollView can have only one child widget"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView has too many children",
            explanation = "A ScrollView can only hold one direct child. If you want more children, wrap them in a container layout.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
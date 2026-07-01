package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation = "A `ScrollView` can only have one child widget. If you want more children, wrap them in a container layout.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "ScrollView",
            "HorizontalScrollView",
            "android.support.v4.widget.NestedScrollView",
            "androidx.core.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                childCount++
            }
            child = child.nextSibling
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A ScrollView can have only one child"
            )
        }
    }
}
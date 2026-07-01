package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView can have only one child",
            explanation = "A `ScrollView` can only have one child widget. If you want more children, wrap them in a container layout.",
            category = Category.CORRECTNESS,
            priority = 6,
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
        if (LintUtils.getChildren(element).size > 1) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "ScrollView can have only one child"
            )
        }
    }
}
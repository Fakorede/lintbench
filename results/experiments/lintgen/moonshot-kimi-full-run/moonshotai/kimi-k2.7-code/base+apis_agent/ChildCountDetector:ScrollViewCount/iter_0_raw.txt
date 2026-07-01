package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(SCROLL_VIEW)

    override fun visitElement(context: XmlContext, element: Element) {
        var directChildCount = 0
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                directChildCount++
            }
        }

        if (directChildCount > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A ScrollView can only contain one direct child; wrap children in a container layout."
            )
        }
    }

    companion object {
        private const val SCROLL_VIEW = "ScrollView"

        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView has too many children",
            explanation = """
                A ScrollView can only have one direct child. If you want more widgets inside it, wrap them in a container layout such as LinearLayout, FrameLayout, or ConstraintLayout.
            """.trimIndent(),
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
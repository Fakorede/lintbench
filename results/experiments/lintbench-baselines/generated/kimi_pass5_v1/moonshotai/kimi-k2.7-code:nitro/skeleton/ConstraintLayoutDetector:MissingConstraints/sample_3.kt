package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor lets you position widgets using design-time attributes such as `layout_editor_absoluteX`. These attributes are not applied at runtime, so a widget that is not properly constrained may appear in a different location on a device.

                To fix this, add constraints to both the horizontal and vertical edges of the widget (for example `layout_constraintStart_toStartOf` and `layout_constraintTop_toTopOf`).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val CONSTRAINT_LAYOUT_TAGS = listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout",
        )

        private val HELPER_VIEWS = setOf(
            "Barrier",
            "Flow",
            "Group",
            "Guideline",
            "Layer",
            "Placeholder",
        )

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintCircle",
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintCircle",
            "layout_constraintTop_toBottomOf",
            "layout_constraintTop_toTopOf",
        )
    }

    override fun getApplicableElements(): Collection<String> = CONSTRAINT_LAYOUT_TAGS

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
                continue
            }

            val child = node as org.w3c.dom.Element
            if (isHelper(child.tagName)) {
                continue
            }

            val hasHorizontal = HORIZONTAL_CONSTRAINTS.any { child.hasAttribute(it) }
            val hasVertical = VERTICAL_CONSTRAINTS.any { child.hasAttribute(it) }

            when {
                !hasHorizontal && !hasVertical -> {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "This view is not constrained, it will jump to the top-left at runtime",
                    )
                }
                !hasHorizontal -> {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "This view is not constrained horizontally",
                    )
                }
                !hasVertical -> {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "This view is not constrained vertically",
                    )
                }
            }
        }
    }

    private fun isHelper(tagName: String): Boolean {
        val simpleName = tagName.substringAfterLast(".")
        return HELPER_VIEWS.contains(simpleName)
    }
}
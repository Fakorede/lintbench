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
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as layout_editor_absoluteX). These attributes are not applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintLeft_toLeftOf", "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf", "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf", "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf", "layout_constraintEnd_toEndOf",
            "layout_constraintCircleRadius"
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            "layout_constraintTop_toTopOf", "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf", "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintCircleRadius"
        )

        private val HELPER_VIEWS = setOf(
            "Guideline", "Barrier", "Group", "Flow", "Layer", "Placeholder", "MockView"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (!parent.tagName.endsWith("ConstraintLayout")) return

        val tag = element.tagName.substringAfterLast('.')
        if (HELPER_VIEWS.contains(tag)) return

        val hasHorizontal = hasAnyAttribute(element, HORIZONTAL_CONSTRAINTS)
        val hasVertical = hasAnyAttribute(element, VERTICAL_CONSTRAINTS)

        if (!hasHorizontal || !hasVertical) {
            val missing = when {
                !hasHorizontal && !hasVertical -> "horizontally and vertically"
                !hasHorizontal -> "horizontally"
                else -> "vertically"
            }
            val message = "This view is not constrained $missing. At runtime, it may jump to a different location unless you add constraints."
            context.report(ISSUE, element, context.getLocation(element), message)
        }
    }

    private fun hasAnyAttribute(element: Element, attributes: List<String>): Boolean {
        return attributes.any { attr ->
            element.hasAttribute(attr) || element.hasAttribute("app:$attr")
        }
    }
}
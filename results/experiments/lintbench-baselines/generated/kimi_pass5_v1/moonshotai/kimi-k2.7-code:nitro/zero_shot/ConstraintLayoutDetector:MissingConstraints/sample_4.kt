package com.android.tools.lint.checks

import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            "MissingConstraints",
            "Missing Constraints in ConstraintLayout",
            """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            IMPLEMENTATION
        )

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintCircle"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintCircle"
        )

        private val CONSTRAINT_LAYOUTS = listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )

        private val HELPERS = listOf(
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Group",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Placeholder",
            "androidx.constraintlayout.widget.Placeholder"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return CONSTRAINT_LAYOUTS
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue

            val childElement = child as Element
            if (childElement.tagName in HELPERS) continue

            val hasHorizontal = hasConstraint(childElement, HORIZONTAL_CONSTRAINTS)
            val hasVertical = hasConstraint(childElement, VERTICAL_CONSTRAINTS)

            if (!hasHorizontal || !hasVertical) {
                val message = buildString {
                    append("This view is not constrained")
                    when {
                        !hasHorizontal && !hasVertical -> append(" horizontally or vertically")
                        !hasHorizontal -> append(" horizontally")
                        else -> append(" vertically")
                    }
                    append(". At runtime it will jump to the ")
                    when {
                        !hasHorizontal && !hasVertical -> append("top-left")
                        !hasHorizontal -> append("left")
                        else -> append("top")
                    }
                    append(" of the screen unless you add constraints")

                    if (hasDesignTimePosition(childElement)) {
                        append("; the designtime position (layout_editor_absoluteX/Y) is not applied at runtime")
                    }
                    append(".")
                }

                context.report(ISSUE, context.getNameLocation(childElement), message)
            }
        }
    }

    private fun hasConstraint(element: Element, names: Set<String>): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.localName in names) return true
        }
        return false
    }

    private fun hasDesignTimePosition(element: Element): Boolean {
        return hasToolAttribute(element, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
                hasToolAttribute(element, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)
    }

    private fun hasToolAttribute(element: Element, localName: String): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.localName == localName && attr.namespaceURI == TOOLS_URI) return true
        }
        return false
    }
}
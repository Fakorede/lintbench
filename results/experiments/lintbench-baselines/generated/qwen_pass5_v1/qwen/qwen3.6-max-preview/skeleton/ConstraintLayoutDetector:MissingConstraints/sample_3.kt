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
import org.w3c.dom.Node

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
            explanation = "The layout editor allows you to place widgets anywhere on the canvas,  and it records the current position with designtime attributes (such as  `layout_editor_absoluteX`). These attributes are **not** applied at  runtime, so if you push your layout on a device, the widgets may appear  in a different location than shown in the editor. To fix this, make sure  a widget has both horizontal and vertical constraints by dragging from  the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val CONSTRAINT_LAYOUTS = listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout"
        )

        private val HELPERS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder", "Layer", "Flow"
        )
    }

    override fun getApplicableElements(): Collection<String>? = CONSTRAINT_LAYOUTS

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue

            val child = node as Element
            if (isHelperView(child)) continue

            val hasHorizontal = hasHorizontalConstraint(child)
            val hasVertical = hasVerticalConstraint(child)

            if (!hasHorizontal || !hasVertical) {
                val missing = buildString {
                    if (!hasHorizontal) append("horizontally")
                    if (!hasHorizontal && !hasVertical) append(" or ")
                    if (!hasVertical) append("vertically")
                }
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "This view is not constrained $missing. At runtime it will jump to (0,0) unless you add constraints."
                )
            }
        }
    }

    private fun isHelperView(element: Element): Boolean {
        val tag = element.tagName.substringAfterLast('.')
        return tag in HELPERS
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        return hasConstraintDirection(element, horizontal = true)
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        return hasConstraintDirection(element, horizontal = false)
    }

    private fun hasConstraintDirection(element: Element, horizontal: Boolean): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val name = attrs.item(i).localName ?: attrs.item(i).nodeName
            if (!name.startsWith("layout_constraint")) continue

            if (horizontal) {
                if (name.contains("Left") || name.contains("Right") ||
                    name.contains("Start") || name.contains("End") ||
                    name.contains("Circle")) {
                    return true
                }
            } else {
                if (name.contains("Top") || name.contains("Bottom") ||
                    name.contains("Baseline") || name.contains("Circle")) {
                    return true
                }
            }
        }
        return false
    }
}
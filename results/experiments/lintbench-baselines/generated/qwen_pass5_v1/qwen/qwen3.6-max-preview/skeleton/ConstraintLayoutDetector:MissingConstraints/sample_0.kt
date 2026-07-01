package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
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
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
                    "and it records the current position with designtime attributes (such as " +
                    "`layout_editor_absoluteX`). These attributes are **not** applied at " +
                    "runtime, so if you push your layout on a device, the widgets may appear " +
                    "in a different location than shown in the editor. To fix this, make sure " +
                    "a widget has both horizontal and vertical constraints by dragging from " +
                    "the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val CONSTRAINT_LAYOUTS = listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout"
        )

        private val HELPER_VIEWS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder", "Layer", "Flow", "CircularFlow"
        )
    }

    override fun getApplicableElements(): Collection<String>? = CONSTRAINT_LAYOUTS

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            val simpleName = childElement.tagName.substringAfterLast('.')
            if (simpleName in HELPER_VIEWS) continue

            checkConstraints(context, childElement)
        }
    }

    private fun checkConstraints(context: XmlContext, element: Element) {
        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.localName ?: attr.name
            if (!name.startsWith("layout_constraint")) continue

            if (name.endsWith("_toLeftOf") || name.endsWith("_toRightOf") ||
                name.endsWith("_toStartOf") || name.endsWith("_toEndOf") ||
                name.endsWith("Circle")) {
                hasHorizontal = true
            }
            if (name.endsWith("_toTopOf") || name.endsWith("_toBottomOf") ||
                name.endsWith("_toBaselineOf") || name.endsWith("Circle")) {
                hasVertical = true
            }
            if (hasHorizontal && hasVertical) return
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = buildString {
                if (!hasHorizontal) append("horizontally ")
                if (!hasVertical) append("vertically")
            }.trim()
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained $missing. At runtime it will jump to (0,0) unless you add constraints."
            )
        }
    }
}
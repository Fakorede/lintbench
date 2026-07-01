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

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "MissingConstraints",
            "Missing Constraints in ConstraintLayout",
            "The layout editor allows you to place widgets anywhere on the canvas, " +
                    "and it records the current position with designtime attributes (such as " +
                    "`layout_editor_absoluteX`). These attributes are **not** applied at " +
                    "runtime, so if you push your layout on a device, the widgets may appear " +
                    "in a different location than shown in the editor. To fix this, make sure " +
                    "a widget has both horizontal and vertical constraints by dragging from " +
                    "the edge connections.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf", "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf", "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf", "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf", "layout_constraintEnd_toEndOf",
            "layout_constraintCircle"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf", "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf", "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf", "layout_constraintCircle"
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (!parent.tagName.endsWith("ConstraintLayout")) return

        val tagName = element.tagName
        if (tagName.endsWith("Guideline") || tagName.endsWith("Barrier") ||
            tagName.endsWith("Group") || tagName.endsWith("Placeholder") ||
            tagName.endsWith("Flow") || tagName.endsWith("MockView")) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as Attr
            val name = attr.localName?.takeIf { it.isNotEmpty() } ?: attr.name.substringAfter(':')
            if (name in HORIZONTAL_CONSTRAINTS) hasHorizontal = true
            if (name in VERTICAL_CONSTRAINTS) hasVertical = true
            if (hasHorizontal && hasVertical) return
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = buildString {
                if (!hasHorizontal) append("horizontally")
                if (!hasHorizontal && !hasVertical) append(" or ")
                if (!hasVertical) append("vertically")
            }
            val direction = if (!hasHorizontal) "left" else "top"
            val message = "This view is not constrained $missing: at runtime it will jump to the $direction unless you add a $missing constraint"
            context.report(ISSUE, element, context.getLocation(element), message)
        }
    }
}
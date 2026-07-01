package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
    }

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (!isConstraintLayout(parent.tagName)) return
        if (isConstraintLayout(element.tagName)) return
        if (isHelperView(element.tagName)) return

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attrName = attributes.item(i).localName ?: attributes.item(i).nodeName
            if (!attrName.startsWith("layout_constraint")) continue

            if (isHorizontalConstraint(attrName)) hasHorizontal = true
            if (isVerticalConstraint(attrName)) hasVertical = true

            if (hasHorizontal && hasVertical) return
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = when {
                !hasHorizontal && !hasVertical -> "horizontal and vertical"
                !hasHorizontal -> "horizontal"
                else -> "vertical"
            }
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained. It only has designtime positions, so it will " +
                    "jump to (0,0) at runtime unless you add the constraints. Missing $missing constraints."
            )
        }
    }

    private fun isConstraintLayout(tag: String): Boolean {
        val simple = tag.substringAfterLast('.')
        return simple == "ConstraintLayout"
    }

    private fun isHelperView(tag: String): Boolean {
        val simple = tag.substringAfterLast('.')
        return simple in listOf(
            "Guideline", "Barrier", "Group", "Placeholder",
            "Layer", "Flow", "MockView"
        )
    }

    private fun isHorizontalConstraint(attrName: String): Boolean {
        return attrName.contains("Left_to") || attrName.contains("Right_to") ||
            attrName.contains("Start_to") || attrName.contains("End_to") ||
            attrName.contains("Circle")
    }

    private fun isVerticalConstraint(attrName: String): Boolean {
        return attrName.contains("Top_to") || attrName.contains("Bottom_to") ||
            attrName.contains("Baseline_to") || attrName.contains("Circle")
    }
}
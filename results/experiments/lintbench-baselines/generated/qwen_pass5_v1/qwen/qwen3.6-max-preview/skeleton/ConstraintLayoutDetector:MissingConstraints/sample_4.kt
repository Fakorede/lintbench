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
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as layout_editor_absoluteX). These attributes are not applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (!parent.tagName.endsWith("ConstraintLayout")) return

        val tagName = element.tagName
        if (tagName.endsWith("Guideline") ||
            tagName.endsWith("Barrier") ||
            tagName.endsWith("Group") ||
            tagName.endsWith("Placeholder") ||
            tagName.endsWith("ConstraintLayout")) {
            return
        }

        val hasHorizontal = hasConstraint(element, horizontal = true)
        val hasVertical = hasConstraint(element, horizontal = false)

        if (!hasHorizontal || !hasVertical) {
            val missing = buildString {
                if (!hasHorizontal) append("horizontal")
                if (!hasHorizontal && !hasVertical) append(" and ")
                if (!hasVertical) append("vertical")
            }
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained. It only has design-time positions, so it will jump to (0,0) at runtime unless you add $missing constraints"
            )
        }
    }

    private fun hasConstraint(element: Element, horizontal: Boolean): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: continue
            if (!name.startsWith("layout_constraint")) continue

            if (name == "layout_constraintCircle") return true

            if (horizontal) {
                if (name.contains("Left") || name.contains("Right") ||
                    name.contains("Start") || name.contains("End") ||
                    name.contains("Horizontal") || name.contains("Width")) {
                    return true
                }
            } else {
                if (name.contains("Top") || name.contains("Bottom") ||
                    name.contains("Baseline") || name.contains("Vertical") ||
                    name.contains("Height")) {
                    return true
                }
            }
        }
        return false
    }
}
package com.android.tools.lint.checks

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

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout",
            "ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName.contains("Guideline") || tagName.contains("Barrier") ||
            tagName.contains("Group") || tagName.contains("Placeholder") ||
            tagName.contains("Layer") || tagName.contains("Flow") ||
            tagName == "include" || tagName == "merge" || tagName == "fragment") {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName
            if (!name.startsWith("layout_constraint")) continue

            if (name.contains("Left") || name.contains("Right") ||
                name.contains("Start") || name.contains("End") ||
                name.contains("Horizontal") || name.contains("Circle") ||
                name.contains("Guide")) {
                hasHorizontal = true
            }
            if (name.contains("Top") || name.contains("Bottom") ||
                name.contains("Baseline") || name.contains("Vertical") ||
                name.contains("Circle") || name.contains("Guide")) {
                hasVertical = true
            }
        }

        val width = element.getAttribute("android:layout_width")
        val height = element.getAttribute("android:layout_height")
        if (width == "match_parent") hasHorizontal = true
        if (height == "match_parent") hasVertical = true

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
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add $missing constraints"
            )
        }
    }

    companion object {
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
            implementation = Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
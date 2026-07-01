package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : LayoutDetector() {
    companion object {
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
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val HELPER_VIEWS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder", "Layer", "Flow", "MockView"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.localName ?: element.tagName
        if (!tagName.endsWith("ConstraintLayout")) return

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            val childTagName = childElement.localName ?: childElement.tagName

            if (isHelperView(childTagName)) continue

            val width = childElement.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_width")
            val height = childElement.getAttributeNS("http://schemas.android.com/apk/res/android", "layout_height")

            val needsHorizontal = width != "match_parent" && width != "fill_parent"
            val needsVertical = height != "match_parent" && height != "fill_parent"

            val hasH = !needsHorizontal || hasHorizontalConstraint(childElement)
            val hasV = !needsVertical || hasVerticalConstraint(childElement)

            if (!hasH || !hasV) {
                val missing = mutableListOf<String>()
                if (!hasH) missing.add("horizontal")
                if (!hasV) missing.add("vertical")
                val message = "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints. Missing ${missing.joinToString(" and ")} constraints."
                context.report(ISSUE, context.getLocation(childElement), message)
            }
        }
    }

    private fun isHelperView(tagName: String): Boolean {
        return HELPER_VIEWS.any { tagName == it || tagName.endsWith(".$it") }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName
            if (name.startsWith("layout_constraint") &&
                (name.contains("Left") || name.contains("Right") ||
                 name.contains("Start") || name.contains("End") ||
                 name.contains("Circle"))) {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName
            if (name.startsWith("layout_constraint") &&
                (name.contains("Top") || name.contains("Bottom") ||
                 name.contains("Baseline") || name.contains("Circle"))) {
                return true
            }
        }
        return false
    }
}
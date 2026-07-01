package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            checkChild(context, childElement)
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tag = element.tagName
        if (isHelperView(tag)) return

        val hasHorizontal = hasHorizontalConstraint(element) || isMatchParent(element, "layout_width")
        val hasVertical = hasVerticalConstraint(element) || isMatchParent(element, "layout_height")

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
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the $missing constraints"
            )
        }
    }

    private fun isHelperView(tag: String): Boolean {
        return tag.endsWith(".Guideline") || tag.endsWith(".Barrier") ||
               tag.endsWith(".Group") || tag.endsWith(".Placeholder") ||
               tag.endsWith(".Layer") || tag.endsWith(".Flow") ||
               tag == "Guideline" || tag == "Barrier" || tag == "Group" ||
               tag == "Placeholder" || tag == "Layer" || tag == "Flow"
    }

    private fun isMatchParent(element: Element, attrName: String): Boolean {
        val value = element.getAttributeNS("http://schemas.android.com/apk/res/android", attrName)
        return value == "match_parent" || value == "fill_parent"
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        return hasConstraint(element, setOf("Left", "Right", "Start", "End", "Horizontal", "Circle"))
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        return hasConstraint(element, setOf("Top", "Bottom", "Baseline", "Vertical", "Circle"))
    }

    private fun hasConstraint(element: Element, keywords: Set<String>): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: attr.nodeName ?: continue
            if (name.startsWith("layout_constraint")) {
                for (keyword in keywords) {
                    if (name.contains(keyword)) return true
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
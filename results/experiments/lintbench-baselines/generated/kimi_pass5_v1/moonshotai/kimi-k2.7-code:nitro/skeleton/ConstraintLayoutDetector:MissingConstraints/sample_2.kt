package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas,
                and it records the current position with designtime attributes (such as
                `layout_editor_absoluteX`). These attributes are not applied at runtime,
                so if you push your layout on a device, the widgets may appear in a
                different location than shown in the editor. To fix this, make sure a
                widget has both horizontal and vertical constraints by dragging from the
                edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        "android.support.constraint.ConstraintLayout",
        "androidx.constraintlayout.widget.ConstraintLayout"
    )

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child == null || child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
                continue
            }

            val childElement = child as org.w3c.dom.Element
            val tag = childElement.tagName
            if (tag == "android.support.constraint.Guideline" ||
                tag == "androidx.constraintlayout.widget.Guideline" ||
                tag == "android.support.constraint.Placeholder" ||
                tag == "androidx.constraintlayout.widget.Placeholder" ||
                tag == "android.support.constraint.Group" ||
                tag == "androidx.constraintlayout.widget.Group" ||
                tag == "android.support.constraint.Barrier" ||
                tag == "androidx.constraintlayout.widget.Barrier" ||
                tag == "android.support.constraint.Flow" ||
                tag == "androidx.constraintlayout.widget.Flow"
            ) {
                continue
            }

            if (!hasHorizontalConstraints(childElement)) {
                context.report(
                    ISSUE,
                    childElement,
                    context.getNameLocation(childElement),
                    "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
                )
            }

            if (!hasVerticalConstraints(childElement)) {
                context.report(
                    ISSUE,
                    childElement,
                    context.getNameLocation(childElement),
                    "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
                )
            }
        }
    }

    private fun hasHorizontalConstraints(element: org.w3c.dom.Element): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) ?: continue
            val name = attr.localName ?: attr.nodeName
            if (name.startsWith("layout_constraint") &&
                (name.contains("Left") || name.contains("Right") || name.contains("Start") || name.contains("End"))
            ) {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraints(element: org.w3c.dom.Element): Boolean {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) ?: continue
            val name = attr.localName ?: attr.nodeName
            if (name.startsWith("layout_constraint") &&
                (name.contains("Top") || name.contains("Bottom") || name.contains("Baseline"))
            ) {
                return true
            }
        }
        return false
    }
}
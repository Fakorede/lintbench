package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is org.w3c.dom.Element) {
                val tagName = node.tagName
                if (isIgnoredTag(tagName)) {
                    continue
                }

                val hasHorizontal = hasHorizontalConstraint(node)
                val hasVertical = hasVerticalConstraint(node)

                if (!hasHorizontal || !hasVertical) {
                    val missing = when {
                        !hasHorizontal && !hasVertical -> "horizontal and vertical"
                        !hasHorizontal -> "horizontal"
                        else -> "vertical"
                    }
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This view is not constrained $missing: at runtime it will jump to the left/top unless you add a constraint"
                    )
                }
            }
        }
    }

    private fun isIgnoredTag(tagName: String): Boolean {
        val name = tagName.substringAfterLast('.')
        return name == "Guideline" ||
               name == "Barrier" ||
               name == "Group" ||
               name == "Placeholder" ||
               name == "Flow" ||
               name == "Helper" ||
               name == "include" ||
               name == "merge"
    }

    private fun hasHorizontalConstraint(element: org.w3c.dom.Element): Boolean {
        for (attr in HORIZONTAL_CONSTRAINTS) {
            if (element.hasAttributeNS(com.android.SdkConstants.AUTO_URI, attr)) {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraint(element: org.w3c.dom.Element): Boolean {
        for (attr in VERTICAL_CONSTRAINTS) {
            if (element.hasAttributeNS(com.android.SdkConstants.AUTO_URI, attr)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are NOT applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_SCOPE
            )
        )
    }
}
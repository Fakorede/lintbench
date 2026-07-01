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

    companion object {
        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf", "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf", "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf", "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf", "layout_constraintEnd_toEndOf",
            "layout_constraintCircle",
            "layout_constraintHorizontal_bias", "layout_constraintHorizontal_chainStyle",
            "layout_constraintHorizontal_weight"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf", "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf", "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf", "layout_constraintBaseline_toTopOf",
            "layout_constraintBaseline_toBottomOf",
            "layout_constraintCircle",
            "layout_constraintVertical_bias", "layout_constraintVertical_chainStyle",
            "layout_constraintVertical_weight"
        )

        private val HELPERS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder", "Layer", "Flow", "ConstraintSet"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

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
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element)
            }
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tagName = element.tagName.substringAfterLast('.')
        if (tagName in HELPERS) return
        if (tagName == "include" || tagName == "merge" || tagName == "fragment") return

        val hasHorizontal = hasConstraint(element, HORIZONTAL_CONSTRAINTS)
        val hasVertical = hasConstraint(element, VERTICAL_CONSTRAINTS)

        if (!hasHorizontal || !hasVertical) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
            )
        }
    }

    private fun hasConstraint(element: Element, constraints: Set<String>): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) ?: continue
            val name = attr.localName ?: attr.nodeName
            if (name in constraints) return true
        }
        return false
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SdkConstants
import com.android.tools.lint.detector.api.XmlContext

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
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records
                the current position with designtime attributes (such as layout_editor_absoluteX).
                These attributes are **not** applied at runtime, so if you push your layout on a device,
                the widgets may appear in a different location than shown in the editor. To fix this,
                make sure a widget has both horizontal and vertical constraints by dragging from the
                edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private val constraintLayoutTags = listOf(
        "androidx.constraintlayout.widget.ConstraintLayout",
        "android.support.constraint.ConstraintLayout",
        "ConstraintLayout"
    )

    override fun getApplicableElements(): Collection<String>? = constraintLayoutTags

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childNodes ?: return
        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            if (isHelperWidget(child)) continue

            val missingHorizontal = !hasHorizontalConstraint(child)
            val missingVertical = !hasVerticalConstraint(child)

            if (!missingHorizontal && !missingVertical) continue

            val location = context.getLocation(child)
            if (missingHorizontal) {
                context.report(
                    ISSUE,
                    child,
                    location,
                    "This view is not constrained horizontally: add a constraint from one of its horizontal edges"
                )
            }
            if (missingVertical) {
                context.report(
                    ISSUE,
                    child,
                    location,
                    "This view is not constrained vertically: add a constraint from one of its vertical edges or baseline"
                )
            }
        }
    }

    private fun isHelperWidget(element: org.w3c.dom.Element): Boolean {
        val name = element.tagName?.substringAfterLast(".") ?: return false
        return name == "Guideline" ||
                name == "Barrier" ||
                name == "Group" ||
                name == "Placeholder" ||
                name == "Flow" ||
                name == "Layer"
    }

    private fun hasHorizontalConstraint(element: org.w3c.dom.Element): Boolean {
        if (element.hasAttributeNS(SdkConstants.AUTO_URI, ATTR_LAYOUT_CONSTRAINT_CIRCLE)) {
            return true
        }
        return HORIZONTAL_CONSTRAINTS.any { element.getAttributeNS(SdkConstants.AUTO_URI, it).isNotBlank() }
    }

    private fun hasVerticalConstraint(element: org.w3c.dom.Element): Boolean {
        if (element.hasAttributeNS(SdkConstants.AUTO_URI, ATTR_LAYOUT_CONSTRAINT_CIRCLE)) {
            return true
        }
        return VERTICAL_CONSTRAINTS.any { element.getAttributeNS(SdkConstants.AUTO_URI, it).isNotBlank() }
    }
}

private const val ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle"

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
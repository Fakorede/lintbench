package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X
import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
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
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, \
                and it records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are **not** applied at \
                runtime, so if you push your layout on a device, the widgets may appear \
                in a different location than shown in the editor. To fix this, make sure \
                a widget has both horizontal and vertical constraints by dragging from \
                the edge connections.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        private const val CONSTRAINT_LAYOUT =
            "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_LEGACY =
            "android.support.constraint.ConstraintLayout"

        private const val ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle"

        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            ATTR_LAYOUT_CONSTRAINT_CIRCLE
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            ATTR_LAYOUT_CONSTRAINT_CIRCLE
        )

        private val HELPER_WIDGETS = setOf(
            "Guideline",
            "Barrier",
            "Group",
            "Placeholder",
            "MockView"
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_LEGACY)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue

            if (child.tagName == "requestFocus") {
                continue
            }

            val simpleName = child.tagName.substringAfterLast('.')
            if (HELPER_WIDGETS.contains(simpleName)) {
                continue
            }

            val hasAbsoluteX = child.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)
            val hasAbsoluteY = child.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)

            if (!hasAbsoluteX && !hasAbsoluteY) {
                continue
            }

            val hasHorizontal = HORIZONTAL_CONSTRAINTS.any {
                child.hasAttributeNS(AUTO_URI, it)
            }
            val hasVertical = VERTICAL_CONSTRAINTS.any {
                child.hasAttributeNS(AUTO_URI, it)
            }

            if (hasAbsoluteX && !hasHorizontal) {
                reportMissingConstraint(
                    context, child, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, horizontal = true
                )
            }

            if (hasAbsoluteY && !hasVertical) {
                reportMissingConstraint(
                    context, child, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, horizontal = false
                )
            }
        }
    }

    private fun reportMissingConstraint(
        context: XmlContext,
        element: Element,
        attributeName: String,
        horizontal: Boolean
    ) {
        val attribute = element.getAttributeNodeNS(TOOLS_URI, attributeName) ?: return
        val direction = if (horizontal) "horizontally" else "vertically"
        val position = if (horizontal) "left" else "top"
        context.report(
            ISSUE,
            attribute,
            context.getLocation(attribute),
            "This view is not constrained $direction. At runtime it will jump to the $position unless you add a $direction constraint."
        )
    }
}
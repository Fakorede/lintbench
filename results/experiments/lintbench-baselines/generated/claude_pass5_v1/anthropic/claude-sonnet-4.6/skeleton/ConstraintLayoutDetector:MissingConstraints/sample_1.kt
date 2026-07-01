package com.android.tools.lint.checks

import com.android.SdkConstants
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
            explanation =
                """
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
            implementation = IMPLEMENTATION,
        )

        private const val CONSTRAINT_LAYOUT_URI =
            "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_OLD_URI =
            "android.support.constraint.ConstraintLayout"

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        // Horizontal constraint attributes
        private val HORIZONTAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintHorizontal_bias",
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
        )

        // Vertical constraint attributes
        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintVertical_bias",
        )

        // These widget types don't need constraints
        private val EXCLUDED_TAGS = setOf(
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            SdkConstants.VIEW_INCLUDE,
            SdkConstants.VIEW_MERGE,
            SdkConstants.VIEW_TAG,
            SdkConstants.REQUEST_FOCUS,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parentNode = element.parentNode as? Element ?: return
        val parentTag = parentNode.tagName ?: return

        // Check if the parent is a ConstraintLayout
        if (parentTag != CONSTRAINT_LAYOUT_URI && parentTag != CONSTRAINT_LAYOUT_OLD_URI) {
            return
        }

        val tag = element.tagName ?: return

        // Skip elements that don't need constraints
        if (tag in EXCLUDED_TAGS) {
            return
        }

        // Check layout_width and layout_height - if they are match_parent or fill_parent, skip
        val layoutWidth = element.getAttributeNS(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_WIDTH)
        val layoutHeight = element.getAttributeNS(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_HEIGHT)

        if (layoutWidth == SdkConstants.VALUE_MATCH_PARENT || layoutWidth == "fill_parent" ||
            layoutHeight == SdkConstants.VALUE_MATCH_PARENT || layoutHeight == "fill_parent") {
            // match_parent widgets don't need constraints in the same way
            // but they still should have them ideally; however, we won't flag these
            return
        }

        val autoUri = SdkConstants.AUTO_URI

        // Check for horizontal constraints
        val hasHorizontalConstraint = HORIZONTAL_CONSTRAINT_ATTRS.any { attr ->
            element.hasAttributeNS(autoUri, attr)
        }

        // Check for vertical constraints
        val hasVerticalConstraint = VERTICAL_CONSTRAINT_ATTRS.any { attr ->
            element.hasAttributeNS(autoUri, attr)
        }

        if (hasHorizontalConstraint && hasVerticalConstraint) {
            return
        }

        // Check if the element has layout_editor_absoluteX or layout_editor_absoluteY
        // which indicates it was placed in the editor without proper constraints
        val hasEditorAbsoluteX = element.hasAttributeNS(autoUri, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)
        val hasEditorAbsoluteY = element.hasAttributeNS(autoUri, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)

        if (!hasHorizontalConstraint && !hasVerticalConstraint) {
            if (hasEditorAbsoluteX || hasEditorAbsoluteY) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints",
                )
            } else {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint",
                )
            }
        } else if (!hasHorizontalConstraint) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint",
            )
        } else {
            // !hasVerticalConstraint
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint",
            )
        }
    }
}
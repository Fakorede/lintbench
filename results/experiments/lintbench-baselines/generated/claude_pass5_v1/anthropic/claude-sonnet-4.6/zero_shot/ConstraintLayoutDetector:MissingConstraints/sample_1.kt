package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks for missing constraints in ConstraintLayout children.
 */
class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private const val CONSTRAINT_LAYOUT_LIB_ARTIFACT = "androidx.constraintlayout:constraintlayout"
        private const val CONSTRAINT_LAYOUT_LIB_ARTIFACT_OLD = "com.android.support.constraint:constraint-layout"

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
            "layout_constraintLeft_creator",
            "layout_constraintRight_creator"
        )

        // Vertical constraint attributes
        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintBaseline_toTopOf",
            "layout_constraintBaseline_toBottomOf",
            "layout_constraintVertical_bias",
            "layout_constraintTop_creator",
            "layout_constraintBottom_creator"
        )

        // Attributes that indicate the widget is positioned via chains or barriers etc.
        private val CONSTRAINT_DIMENSION_ATTRS = setOf(
            "layout_constraintDimensionRatio"
        )

        // Design-time absolute position attributes (these are not applied at runtime)
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        // Special tags that don't need constraints
        private val EXCLUDED_TAGS = setOf(
            "Guideline",
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "Group",
            "android.support.constraint.Group",
            "androidx.constraintlayout.widget.Group",
            "Barrier",
            "android.support.constraint.Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "MockView",
            "android.support.constraint.MockView",
            "androidx.constraintlayout.widget.MockView",
            "ConstraintHelper",
            "android.support.constraint.ConstraintHelper",
            "androidx.constraintlayout.widget.ConstraintHelper",
            "Flow",
            "androidx.constraintlayout.helper.widget.Flow"
        )

        @JvmField
        val ISSUE = Issue.create(
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
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private fun isConstraintLayout(element: Element): Boolean {
            val tag = element.tagName
            return tag == CLASS_CONSTRAINT_LAYOUT ||
                tag == "android.support.constraint.ConstraintLayout" ||
                tag == "androidx.constraintlayout.widget.ConstraintLayout" ||
                tag == "ConstraintLayout" ||
                tag.endsWith(".ConstraintLayout")
        }

        private fun isExcludedTag(element: Element): Boolean {
            val tag = element.tagName
            return tag in EXCLUDED_TAGS ||
                tag.endsWith("Guideline") ||
                tag.endsWith("Barrier") ||
                tag.endsWith("Group") ||
                tag.endsWith("ConstraintHelper") ||
                tag.endsWith("Flow")
        }

        private fun hasHorizontalConstraint(element: Element): Boolean {
            // Check app/auto namespace
            for (attr in HORIZONTAL_CONSTRAINT_ATTRS) {
                if (element.hasAttributeNS(AUTO_URI, attr)) {
                    return true
                }
            }
            // Check if layout_width is 0dp (match_constraint) which implies constraints
            val widthValue = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
            if (widthValue == "0dp") {
                // If width is 0dp, there should be constraints, but we still check
                // Actually 0dp without constraints is the problem, so don't return true here
            }
            return false
        }

        private fun hasVerticalConstraint(element: Element): Boolean {
            // Check app/auto namespace
            for (attr in VERTICAL_CONSTRAINT_ATTRS) {
                if (element.hasAttributeNS(AUTO_URI, attr)) {
                    return true
                }
            }
            return false
        }

        private fun hasAbsolutePosition(element: Element): Boolean {
            return element.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
                element.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        // We want to check all ConstraintLayout elements
        return listOf(
            CLASS_CONSTRAINT_LAYOUT,
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element)) {
            return
        }

        // Iterate over children
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) {
                continue
            }

            val child = node

            // Skip excluded tags (Guideline, Barrier, Group, etc.)
            if (isExcludedTag(child)) {
                continue
            }

            // Skip tags with a tag name that is just "tag" or other non-widget elements
            val tagName = child.tagName
            if (tagName == "requestFocus" || tagName == "include" || tagName == "merge") {
                continue
            }

            val missingHorizontal = !hasHorizontalConstraint(child)
            val missingVertical = !hasVerticalConstraint(child)

            if (missingHorizontal || missingVertical) {
                // Only report if the child has absolute position attributes
                // (i.e., it was placed in the editor without proper constraints)
                // OR if it simply has no constraints at all
                val hasAbsPos = hasAbsolutePosition(child)

                if (missingHorizontal && missingVertical) {
                    val message = if (hasAbsPos) {
                        "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
                    } else {
                        "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
                    }
                    // Report missing both constraints
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This view is not constrained. It only has designtime positions, " +
                            "so it will jump to (0,0) at runtime unless you add the constraints"
                    )
                } else if (missingHorizontal) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This view is not constrained horizontally: at runtime it will jump to the " +
                            "left unless you add a horizontal constraint"
                    )
                } else {
                    // missingVertical
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This view is not constrained vertically: at runtime it will jump to the " +
                            "top unless you add a vertical constraint"
                    )
                }
            }
        }
    }
}
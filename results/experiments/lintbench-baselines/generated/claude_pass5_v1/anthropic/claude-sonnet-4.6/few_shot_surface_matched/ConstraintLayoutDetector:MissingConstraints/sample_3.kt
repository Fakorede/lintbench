package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_MERGE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    companion object {
        private const val CONSTRAINT_LAYOUT = "android.support.constraint.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_ANDROIDX = "androidx.constraintlayout.widget.ConstraintLayout"

        private const val ATTR_LAYOUT_LEFT_TO_LEFT_OF = "layout_constraintLeft_toLeftOf"
        private const val ATTR_LAYOUT_LEFT_TO_RIGHT_OF = "layout_constraintLeft_toRightOf"
        private const val ATTR_LAYOUT_RIGHT_TO_LEFT_OF = "layout_constraintRight_toLeftOf"
        private const val ATTR_LAYOUT_RIGHT_TO_RIGHT_OF = "layout_constraintRight_toRightOf"
        private const val ATTR_LAYOUT_START_TO_START_OF = "layout_constraintStart_toStartOf"
        private const val ATTR_LAYOUT_START_TO_END_OF = "layout_constraintStart_toEndOf"
        private const val ATTR_LAYOUT_END_TO_START_OF = "layout_constraintEnd_toStartOf"
        private const val ATTR_LAYOUT_END_TO_END_OF = "layout_constraintEnd_toEndOf"

        private const val ATTR_LAYOUT_TOP_TO_TOP_OF = "layout_constraintTop_toTopOf"
        private const val ATTR_LAYOUT_TOP_TO_BOTTOM_OF = "layout_constraintTop_toBottomOf"
        private const val ATTR_LAYOUT_BOTTOM_TO_TOP_OF = "layout_constraintBottom_toTopOf"
        private const val ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF = "layout_constraintBottom_toBottomOf"
        private const val ATTR_LAYOUT_BASELINE_TO_BASELINE_OF = "layout_constraintBaseline_toBaselineOf"

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val VALUE_MATCH_PARENT = "match_parent"
        private const val VALUE_FILL_PARENT = "fill_parent"

        private val HORIZONTAL_CONSTRAINT_ATTRS = listOf(
            ATTR_LAYOUT_LEFT_TO_LEFT_OF,
            ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
            ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
            ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
            ATTR_LAYOUT_START_TO_START_OF,
            ATTR_LAYOUT_START_TO_END_OF,
            ATTR_LAYOUT_END_TO_START_OF,
            ATTR_LAYOUT_END_TO_END_OF
        )

        private val VERTICAL_CONSTRAINT_ATTRS = listOf(
            ATTR_LAYOUT_TOP_TO_TOP_OF,
            ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
            ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
            ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
            ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation =
                "The layout editor allows you to place widgets anywhere on the canvas, " +
                "and it records the current position with designtime attributes (such as " +
                "`layout_editor_absoluteX`). These attributes are **not** applied at " +
                "runtime, so if you push your layout on a device, the widgets may appear " +
                "in a different location than shown in the editor. To fix this, make sure " +
                "a widget has both horizontal and vertical constraints by dragging from " +
                "the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_ANDROIDX)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                checkChild(context, child)
            }
            child = child.nextSibling
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tagName = child.tagName ?: return

        // Skip include, merge, and guideline tags
        if (tagName == TAG_INCLUDE || tagName == VIEW_INCLUDE || tagName == VIEW_MERGE) {
            return
        }

        // Guideline elements don't need constraints
        if (tagName.endsWith("Guideline")) {
            return
        }

        // Check if width/height is match_parent — if so, constraints may not be needed
        val layoutWidth = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val layoutHeight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        val widthIsMatchParent = layoutWidth == VALUE_MATCH_PARENT || layoutWidth == VALUE_FILL_PARENT
        val heightIsMatchParent = layoutHeight == VALUE_MATCH_PARENT || layoutHeight == VALUE_FILL_PARENT

        val hasHorizontalConstraint = widthIsMatchParent || hasAnyAttribute(child, HORIZONTAL_CONSTRAINT_ATTRS)
        val hasVerticalConstraint = heightIsMatchParent || hasAnyAttribute(child, VERTICAL_CONSTRAINT_ATTRS)

        if (hasHorizontalConstraint && hasVerticalConstraint) {
            return
        }

        // Only report if the element has designtime absolute positioning attributes,
        // or if it simply has no constraints at all.
        val hasAbsoluteX = child.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)
        val hasAbsoluteY = child.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)

        if (!hasHorizontalConstraint && !hasVerticalConstraint) {
            if (!hasAbsoluteX && !hasAbsoluteY) {
                // No constraints and no designtime positioning — still report
                val idAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
                val location = if (idAttr != null) {
                    context.getValueLocation(idAttr)
                } else {
                    context.getLocation(child)
                }
                context.report(
                    ISSUE,
                    child,
                    location,
                    "This view is not constrained. It only has designtime positions, " +
                        "so it will jump to (0,0) at runtime unless you add the constraints"
                )
            } else {
                val idAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
                val location = if (idAttr != null) {
                    context.getValueLocation(idAttr)
                } else {
                    context.getLocation(child)
                }
                context.report(
                    ISSUE,
                    child,
                    location,
                    "This view is not constrained. It only has designtime positions, " +
                        "so it will jump to (0,0) at runtime unless you add the constraints"
                )
            }
        } else if (!hasHorizontalConstraint) {
            val idAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
            val location = if (idAttr != null) {
                context.getValueLocation(idAttr)
            } else {
                context.getLocation(child)
            }
            context.report(
                ISSUE,
                child,
                location,
                "This view is not constrained horizontally: at runtime it will jump to the " +
                    "left unless you add a horizontal constraint"
            )
        } else {
            // !hasVerticalConstraint
            val idAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
            val location = if (idAttr != null) {
                context.getValueLocation(idAttr)
            } else {
                context.getLocation(child)
            }
            context.report(
                ISSUE,
                child,
                location,
                "This view is not constrained vertically: at runtime it will jump to the " +
                    "top unless you add a vertical constraint"
            )
        }
    }

    private fun hasAnyAttribute(element: Element, attributes: List<String>): Boolean {
        for (attr in attributes) {
            if (element.hasAttributeNS(AUTO_URI, attr)) {
                return true
            }
        }
        return false
    }
}
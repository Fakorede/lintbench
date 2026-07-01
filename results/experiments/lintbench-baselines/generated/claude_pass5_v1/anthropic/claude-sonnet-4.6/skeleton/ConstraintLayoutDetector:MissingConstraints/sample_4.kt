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

        private const val CONSTRAINT_LAYOUT =
            "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_LEGACY =
            "android.support.constraint.ConstraintLayout"

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

        private const val ATTR_LAYOUT_BASELINE_TO_BASELINE_OF =
            "layout_constraintBaseline_toBaselineOf"

        private const val ATTR_LAYOUT_CENTER_IN_PARENT = "layout_centerInParent"
        private const val ATTR_LAYOUT_CENTER_HORIZONTAL = "layout_centerHorizontal"
        private const val ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical"

        private val HORIZONTAL_CONSTRAINT_ATTRS = setOf(
            ATTR_LAYOUT_LEFT_TO_LEFT_OF,
            ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
            ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
            ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
            ATTR_LAYOUT_START_TO_START_OF,
            ATTR_LAYOUT_START_TO_END_OF,
            ATTR_LAYOUT_END_TO_START_OF,
            ATTR_LAYOUT_END_TO_END_OF,
            ATTR_LAYOUT_CENTER_IN_PARENT,
            ATTR_LAYOUT_CENTER_HORIZONTAL,
        )

        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            ATTR_LAYOUT_TOP_TO_TOP_OF,
            ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
            ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
            ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
            ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
            ATTR_LAYOUT_CENTER_IN_PARENT,
            ATTR_LAYOUT_CENTER_VERTICAL,
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        CONSTRAINT_LAYOUT,
        CONSTRAINT_LAYOUT_LEGACY,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue
            val child = node

            val tag = child.tagName
            // Skip special ConstraintLayout children that don't need constraints
            if (tag == "Guideline" ||
                tag == "androidx.constraintlayout.widget.Guideline" ||
                tag == "android.support.constraint.Guideline" ||
                tag == "Group" ||
                tag == "androidx.constraintlayout.widget.Group" ||
                tag == "Barrier" ||
                tag == "androidx.constraintlayout.widget.Barrier" ||
                tag == "android.support.constraint.Barrier"
            ) {
                continue
            }

            // Check if the child has a visibility="gone" attribute — skip if gone
            val visibility = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VISIBILITY)
            if (visibility == "gone") {
                continue
            }

            val hasHorizontal = hasHorizontalConstraint(child)
            val hasVertical = hasVerticalConstraint(child)

            if (!hasHorizontal || !hasVertical) {
                val missing = when {
                    !hasHorizontal && !hasVertical -> "horizontal and vertical"
                    !hasHorizontal -> "horizontal"
                    else -> "vertical"
                }
                context.report(
                    ISSUE,
                    child,
                    context.getNameLocation(child),
                    "This view is not constrained. It only has designtime positions, " +
                        "so it will jump to (0,0) at runtime unless you add the constraints: " +
                        "Add missing $missing constraints",
                )
            }
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        val appNs = SdkConstants.AUTO_URI
        for (attr in HORIZONTAL_CONSTRAINT_ATTRS) {
            val value = element.getAttributeNS(appNs, attr)
            if (value.isNotEmpty()) return true
        }
        return false
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        val appNs = SdkConstants.AUTO_URI
        for (attr in VERTICAL_CONSTRAINT_ATTRS) {
            val value = element.getAttributeNS(appNs, attr)
            if (value.isNotEmpty()) return true
        }
        return false
    }
}
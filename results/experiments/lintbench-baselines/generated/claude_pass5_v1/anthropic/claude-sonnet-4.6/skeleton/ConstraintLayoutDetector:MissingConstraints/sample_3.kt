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
            implementation = IMPLEMENTATION,
        )

        private const val CONSTRAINT_LAYOUT_URI = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_OLD = "android.support.constraint.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_GUIDELINE = "androidx.constraintlayout.widget.Guideline"
        private const val CONSTRAINT_LAYOUT_GUIDELINE_OLD = "android.support.constraint.Guideline"
        private const val CONSTRAINT_LAYOUT_BARRIER = "androidx.constraintlayout.widget.Barrier"
        private const val CONSTRAINT_LAYOUT_GROUP = "androidx.constraintlayout.widget.Group"
        private const val CONSTRAINT_LAYOUT_FLOW = "androidx.constraintlayout.helper.widget.Flow"
        private const val CONSTRAINT_LAYOUT_LAYER = "androidx.constraintlayout.helper.widget.Layer"

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"

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
            "layout_toLeftOf",
            "layout_toRightOf",
            "layout_toStartOf",
            "layout_toEndOf"
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
            "layout_above",
            "layout_below"
        )

        // Widgets that do not need constraints
        private val EXCLUDED_TAGS = setOf(
            CONSTRAINT_LAYOUT_GUIDELINE,
            CONSTRAINT_LAYOUT_GUIDELINE_OLD,
            CONSTRAINT_LAYOUT_BARRIER,
            CONSTRAINT_LAYOUT_GROUP,
            CONSTRAINT_LAYOUT_FLOW,
            CONSTRAINT_LAYOUT_LAYER,
            "Guideline",
            "Barrier",
            "Group",
            "Flow",
            "Layer"
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        CONSTRAINT_LAYOUT_URI,
        CONSTRAINT_LAYOUT_OLD,
        "ConstraintLayout"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) continue

            val tagName = child.tagName ?: continue

            // Skip excluded widget types that don't need constraints
            if (tagName in EXCLUDED_TAGS) continue

            // Skip tags that are just "include", "merge", "requestFocus", etc.
            if (tagName == "include" || tagName == "merge" || tagName == "requestFocus" ||
                tagName == "fragment" || tagName == "tag") continue

            // Check if it's a Guideline by local name suffix
            val localName = if (tagName.contains('.')) tagName.substringAfterLast('.') else tagName
            if (localName == "Guideline" || localName == "Barrier" ||
                localName == "Group" || localName == "Flow" || localName == "Layer") continue

            val appNs = SdkConstants.AUTO_URI
            val androidNs = SdkConstants.ANDROID_URI

            // Check layout_width and layout_height - if they use match_constraint (0dp) or wrap
            // we still need constraints
            val layoutWidth = child.getAttributeNS(androidNs, ATTR_LAYOUT_WIDTH)
            val layoutHeight = child.getAttributeNS(androidNs, ATTR_LAYOUT_HEIGHT)

            // If the widget has match_parent for both dimensions it may be intentional
            // but ConstraintLayout doesn't support match_parent properly; still check constraints
            var hasHorizontalConstraint = false
            var hasVerticalConstraint = false

            // Check for match_parent as a special case - technically shouldn't be used in CL
            // but if both are match_parent, skip the constraint check
            if (layoutWidth == SdkConstants.VALUE_MATCH_PARENT &&
                layoutHeight == SdkConstants.VALUE_MATCH_PARENT) {
                continue
            }

            val attrs = child.attributes
            for (j in 0 until attrs.length) {
                val attr = attrs.item(j)
                val attrName = attr.localName ?: continue
                val attrNs = attr.namespaceURI ?: continue

                if (attrNs != appNs && attrNs != androidNs) continue

                if (!hasHorizontalConstraint && attrName in HORIZONTAL_CONSTRAINT_ATTRS) {
                    hasHorizontalConstraint = true
                }
                if (!hasVerticalConstraint && attrName in VERTICAL_CONSTRAINT_ATTRS) {
                    hasVerticalConstraint = true
                }

                if (hasHorizontalConstraint && hasVerticalConstraint) break
            }

            if (!hasHorizontalConstraint || !hasVerticalConstraint) {
                val missing = when {
                    !hasHorizontalConstraint && !hasVerticalConstraint ->
                        "both a horizontal and vertical constraint"
                    !hasHorizontalConstraint -> "a horizontal constraint"
                    else -> "a vertical constraint"
                }
                context.report(
                    issue = ISSUE,
                    location = context.getElementLocation(child),
                    message = "This view is not constrained. It only has designtime positions, " +
                        "so it will jump to (0,0) at runtime unless you add the constraints. " +
                        "Also missing $missing"
                )
            }
        }
    }
}
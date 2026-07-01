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

        private const val CONSTRAINT_LAYOUT_URI =
            "http://schemas.android.com/apk/res-auto"

        private const val CONSTRAINT_LAYOUT =
            "androidx.constraintlayout.widget.ConstraintLayout"

        private const val CONSTRAINT_LAYOUT_LEGACY =
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
            "layout_constraintHorizontal_bias"
        )

        // Vertical constraint attributes
        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintVertical_bias"
        )

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val VALUE_MATCH_PARENT = "match_parent"
        private const val VALUE_FILL_PARENT = "fill_parent"

        // Tags that should not be checked for constraints
        private val EXCLUDED_TAGS = setOf(
            "Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "requestFocus",
            "tag",
            "include",
            "merge",
            SdkConstants.VIEW_MERGE,
            SdkConstants.VIEW_INCLUDE,
            SdkConstants.VIEW_FRAGMENT
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        CONSTRAINT_LAYOUT,
        CONSTRAINT_LAYOUT_LEGACY
    )

    override fun visitElement(context: XmlContext, element: Element) {
        // Iterate over all direct children of the ConstraintLayout
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue

            val child = node
            val tagName = child.tagName ?: continue

            // Skip excluded tags
            val localName = if (tagName.contains(".")) tagName.substringAfterLast(".") else tagName
            if (tagName in EXCLUDED_TAGS || localName in EXCLUDED_TAGS) continue

            // Skip elements that use match_parent for width or height — they don't need constraints
            // in that dimension
            val appNs = SdkConstants.AUTO_URI
            val androidNs = SdkConstants.ANDROID_URI

            val layoutWidth = child.getAttributeNS(androidNs, ATTR_LAYOUT_WIDTH)
            val layoutHeight = child.getAttributeNS(androidNs, ATTR_LAYOUT_HEIGHT)

            val widthIsMatchParent = layoutWidth == VALUE_MATCH_PARENT || layoutWidth == VALUE_FILL_PARENT
            val heightIsMatchParent = layoutHeight == VALUE_MATCH_PARENT || layoutHeight == VALUE_FILL_PARENT

            // Check if the child has horizontal constraints
            val hasHorizontalConstraint = widthIsMatchParent || hasAnyAttribute(child, appNs, HORIZONTAL_CONSTRAINT_ATTRS)

            // Check if the child has vertical constraints
            val hasVerticalConstraint = heightIsMatchParent || hasAnyAttribute(child, appNs, VERTICAL_CONSTRAINT_ATTRS)

            if (!hasHorizontalConstraint || !hasVerticalConstraint) {
                // Only report if there are editor absolute position attributes (meaning it was
                // placed in the editor without proper constraints) OR if it simply lacks constraints
                val missingDimensions = buildList {
                    if (!hasHorizontalConstraint) add("horizontal")
                    if (!hasVerticalConstraint) add("vertical")
                }

                val message = if (missingDimensions.size == 2) {
                    "This view is not constrained. It only has designtime positions, so it will " +
                        "jump to (0,0) at runtime unless you add the constraints"
                } else {
                    "This view is not constrained ${missingDimensions.first()}ly. " +
                        "At runtime the view may jump to a different position."
                }

                context.report(
                    issue = ISSUE,
                    scope = child,
                    location = context.getElementLocation(child),
                    message = message
                )
            }
        }
    }

    private fun hasAnyAttribute(element: Element, namespace: String, attributes: Set<String>): Boolean {
        for (attr in attributes) {
            val value = element.getAttributeNS(namespace, attr)
            if (value.isNotEmpty()) return true
        }
        return false
    }
}
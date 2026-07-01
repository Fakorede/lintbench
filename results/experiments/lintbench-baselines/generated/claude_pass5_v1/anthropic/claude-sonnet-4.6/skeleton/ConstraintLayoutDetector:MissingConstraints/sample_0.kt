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

        private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_LEGACY = "android.support.constraint.ConstraintLayout"

        // Horizontal constraint attributes
        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintHorizontal_bias",
        )

        // Vertical constraint attributes
        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintVertical_bias",
        )

        // Tags that are not real views and should be skipped
        private val EXCLUDED_TAGS = setOf(
            "Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Barrier",
            "Group",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group",
            "Layer",
            "androidx.constraintlayout.widget.Layer",
            "androidx.constraintlayout.helper.widget.Layer",
            "Flow",
            "androidx.constraintlayout.helper.widget.Flow",
            SdkConstants.VIEW_TAG,
            SdkConstants.VIEW_INCLUDE,
            SdkConstants.VIEW_MERGE,
            SdkConstants.REQUEST_FOCUS,
            SdkConstants.TAG,
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

            val tagName = child.tagName ?: continue

            // Skip excluded tags
            if (tagName in EXCLUDED_TAGS) continue
            // Skip tags ending with known non-view suffixes
            val localTag = tagName.substringAfterLast('.')
            if (localTag in setOf("Guideline", "Barrier", "Group", "Layer", "Flow")) continue

            // Check if it's a zero-size dimension that might be a chain helper or similar
            // Skip <include> tags
            if (tagName == SdkConstants.VIEW_INCLUDE || tagName == "include") continue
            if (tagName == SdkConstants.VIEW_MERGE || tagName == "merge") continue
            if (tagName == SdkConstants.REQUEST_FOCUS || tagName == "requestFocus") continue
            if (tagName == SdkConstants.TAG || tagName == "tag") continue

            val appNs = SdkConstants.AUTO_URI

            // Check for visibility gone - if gone, constraints may not matter but still flag
            // Actually we should still flag, editor does too.

            // Check if element has layout_constraintWidth_percent or similar that imply constraints
            // Check horizontal constraints
            val hasHorizontal = hasHorizontalConstraint(child, appNs)
            val hasVertical = hasVerticalConstraint(child, appNs)

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
                    "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints",
                )
            }
        }
    }

    private fun hasHorizontalConstraint(element: Element, appNs: String): Boolean {
        for (attr in HORIZONTAL_CONSTRAINTS) {
            if (element.hasAttributeNS(appNs, attr)) return true
        }
        // Also check layout_constraintCircle (circular constraints count for both axes)
        if (element.hasAttributeNS(appNs, "layout_constraintCircle")) return true
        // Check layout_constraintHorizontal_chainStyle doesn't imply constraint itself
        // Check if width is set to match_constraint (0dp) with percent
        if (element.hasAttributeNS(appNs, "layout_constraintWidth_percent")) return true
        if (element.hasAttributeNS(appNs, "layout_constraintWidth_default")) return true
        return false
    }

    private fun hasVerticalConstraint(element: Element, appNs: String): Boolean {
        for (attr in VERTICAL_CONSTRAINTS) {
            if (element.hasAttributeNS(appNs, attr)) return true
        }
        // Circular constraint counts for both
        if (element.hasAttributeNS(appNs, "layout_constraintCircle")) return true
        if (element.hasAttributeNS(appNs, "layout_constraintHeight_percent")) return true
        if (element.hasAttributeNS(appNs, "layout_constraintHeight_default")) return true
        return false
    }
}
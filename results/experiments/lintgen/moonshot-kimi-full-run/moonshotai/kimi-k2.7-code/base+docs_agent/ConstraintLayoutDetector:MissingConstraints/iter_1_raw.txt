package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
import com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
import com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF
import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X
import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y
import com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_END_TO_START_OF
import com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF
import com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF
import com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_START_TO_START_OF
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ConstraintLayoutDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (!isConstraintLayout(parent)) return
        if (isConstraintHelper(element)) return

        val hasHorizontal = hasHorizontalConstraint(element)
        val hasVertical = hasVerticalConstraint(element)
        if (hasHorizontal && hasVertical) return

        val hasDesignTimePosition =
            element.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
                    element.hasAttributeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)
        if (!hasDesignTimePosition) return

        val direction = when {
            !hasHorizontal && !hasVertical -> "horizontally or vertically"
            !hasHorizontal -> "horizontally"
            else -> "vertically"
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This view is not constrained $direction. Designtime positions will be ignored at runtime; add constraints."
        )
    }

    private fun isConstraintLayout(element: Element): Boolean {
        val tag = element.tagName
        return tag == CONSTRAINT_LAYOUT || tag == CONSTRAINT_LAYOUT_OLD
    }

    private fun isConstraintHelper(element: Element): Boolean {
        return element.tagName in CONSTRAINT_HELPERS
    }

    private fun hasHorizontalConstraint(element: Element): Boolean =
        HORIZONTAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }

    private fun hasVerticalConstraint(element: Element): Boolean =
        VERTICAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records \
                the current position with designtime attributes (such as layout_editor_absoluteX). \
                These attributes are not applied at runtime, so if you push your layout on a device, \
                the widgets may appear in a different location than shown in the editor. To fix this, \
                make sure a widget has both horizontal and vertical constraints by dragging from the \
                edge connections.
            """.trimIndent().replace("\\\n", ""),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_OLD = "android.support.constraint.ConstraintLayout"

        private const val CLASS_GUIDELINE = "androidx.constraintlayout.widget.Guideline"
        private const val CLASS_GUIDELINE_OLD = "android.support.constraint.Guideline"
        private const val CLASS_BARRIER = "androidx.constraintlayout.widget.Barrier"
        private const val CLASS_BARRIER_OLD = "android.support.constraint.Barrier"
        private const val CLASS_GROUP = "androidx.constraintlayout.widget.Group"
        private const val CLASS_GROUP_OLD = "android.support.constraint.Group"
        private const val CLASS_PLACEHOLDER = "androidx.constraintlayout.widget.Placeholder"
        private const val CLASS_PLACEHOLDER_OLD = "android.support.constraint.Placeholder"

        private val HORIZONTAL_CONSTRAINTS = listOf(
            ATTR_LAYOUT_LEFT_TO_LEFT_OF,
            ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
            ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
            ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
            ATTR_LAYOUT_START_TO_END_OF,
            ATTR_LAYOUT_START_TO_START_OF,
            ATTR_LAYOUT_END_TO_START_OF,
            ATTR_LAYOUT_END_TO_END_OF
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            ATTR_LAYOUT_TOP_TO_TOP_OF,
            ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
            ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
            ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
            ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
        )

        private val CONSTRAINT_HELPERS = setOf(
            CLASS_GUIDELINE,
            CLASS_GUIDELINE_OLD,
            CLASS_BARRIER,
            CLASS_BARRIER_OLD,
            CLASS_GROUP,
            CLASS_GROUP_OLD,
            CLASS_PLACEHOLDER,
            CLASS_PLACEHOLDER_OLD
        )
    }
}
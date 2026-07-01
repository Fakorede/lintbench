package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
import com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
import com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF
import com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_END_TO_START_OF
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF
import com.android.SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF
import com.android.SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF
import com.android.SdkConstants.ATTR_LAYOUT_START_TO_START_OF
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(
        CONSTRAINT_LAYOUT,
        CONSTRAINT_LAYOUT_OLD
    )

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tag = element.tagName
        if (tag in CONSTRAINT_HELPERS) return
        if (tag in IGNORED_TAGS) return

        val missingHorizontal = !hasHorizontalConstraint(element)
        val missingVertical = !hasVerticalConstraint(element)
        if (!missingHorizontal && !missingVertical) return

        val direction = when {
            missingHorizontal && missingVertical -> "horizontally or vertically"
            missingHorizontal -> "horizontally"
            else -> "vertically"
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This view is not constrained $direction. Designtime positions will be ignored at runtime; add constraints."
        )
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        if (HORIZONTAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }) {
            return true
        }
        val width = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        return width == VALUE_MATCH_PARENT || width == VALUE_FILL_PARENT
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        if (VERTICAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }) {
            return true
        }
        val height = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
        return height == VALUE_MATCH_PARENT || height == VALUE_FILL_PARENT
    }

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

        private val IGNORED_TAGS = setOf(
            "include",
            "merge",
            "fragment",
            "view",
            "requestFocus"
        )
    }
}
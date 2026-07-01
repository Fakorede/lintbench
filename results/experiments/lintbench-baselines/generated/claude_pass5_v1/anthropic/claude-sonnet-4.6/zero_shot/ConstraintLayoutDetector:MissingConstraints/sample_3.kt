/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT
import com.android.SdkConstants.FQCN_GUIDELINE
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks that widgets inside a ConstraintLayout have proper horizontal
 * and vertical constraints defined.
 */
class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
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
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        // Constraint attribute prefixes used in the app namespace (AUTO_URI)
        private const val ATTR_LAYOUT_CONSTRAINT_PREFIX = "layout_constraint"

        // Attributes that establish horizontal constraints
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

        // Attributes that establish vertical constraints
        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintBaseline_toTopOf",
            "layout_constraintBaseline_toBottomOf",
            "layout_constraintVertical_bias"
        )

        // Tags that are allowed to not have constraints (e.g. Guideline)
        private val EXCLUDED_TAGS = setOf(
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Guideline",
            FQCN_GUIDELINE
        )

        // Simple tag names that are excluded
        private val EXCLUDED_SIMPLE_TAGS = setOf(
            "Guideline"
        )

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        // Constraint layout class names
        private val CONSTRAINT_LAYOUT_NAMES = setOf(
            CLASS_CONSTRAINT_LAYOUT,
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        // We want to check all elements; we'll filter by parent being a ConstraintLayout
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parentNode = element.parentNode as? Element ?: return

        // Check if the parent is a ConstraintLayout
        val parentTag = parentNode.tagName ?: return
        if (!isConstraintLayout(parentTag)) {
            return
        }

        val tag = element.tagName ?: return

        // Skip excluded widget types (like Guideline)
        if (isExcludedTag(tag)) {
            return
        }

        // Check if layout_width or layout_height is match_parent — if so,
        // the constraint in that direction is implicit
        val layoutWidth = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val layoutHeight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        val widthIsMatchParent = layoutWidth == VALUE_MATCH_PARENT
        val heightIsMatchParent = layoutHeight == VALUE_MATCH_PARENT

        // Collect all attribute names in the app namespace
        val attrs = element.attributes
        val appAttrNames = mutableSetOf<String>()
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val ns = attr.namespaceURI
            if (ns == AUTO_URI) {
                appAttrNames.add(attr.localName)
            }
        }

        // Check for horizontal constraints
        val hasHorizontalConstraint = widthIsMatchParent ||
            appAttrNames.any { it in HORIZONTAL_CONSTRAINT_ATTRS } ||
            hasHorizontalConstraintInAttrs(appAttrNames)

        // Check for vertical constraints
        val hasVerticalConstraint = heightIsMatchParent ||
            appAttrNames.any { it in VERTICAL_CONSTRAINT_ATTRS } ||
            hasVerticalConstraintInAttrs(appAttrNames)

        if (hasHorizontalConstraint && hasVerticalConstraint) {
            return
        }

        // Only report if there's at least one designtime positioning attribute,
        // or if there are no constraints at all (widget has no constraints)
        val missingHorizontal = !hasHorizontalConstraint
        val missingVertical = !hasVerticalConstraint

        if (!missingHorizontal && !missingVertical) {
            return
        }

        // Build the error message
        val widgetId = element.getAttributeNS(ANDROID_URI, ATTR_ID)
        val widgetName = if (widgetId.isNotEmpty()) {
            val id = widgetId.removePrefix("@+id/").removePrefix("@id/")
            "`$id`"
        } else {
            "This view"
        }

        val message = when {
            missingHorizontal && missingVertical ->
                "$widgetName is not constrained horizontally and vertically: " +
                    "at runtime it will jump to (0, 0) unless you add the constraints"
            missingHorizontal ->
                "$widgetName is not constrained horizontally: " +
                    "at runtime it will jump to the left unless you add a horizontal constraint"
            else ->
                "$widgetName is not constrained vertically: " +
                    "at runtime it will jump to the top unless you add a vertical constraint"
        }

        context.report(
            issue = ISSUE,
            scope = element,
            location = context.getNameLocation(element),
            message = message
        )
    }

    /**
     * Returns true if the given tag name corresponds to a ConstraintLayout.
     */
    private fun isConstraintLayout(tag: String): Boolean {
        if (tag in CONSTRAINT_LAYOUT_NAMES) return true
        // Handle simple class name
        if (tag == "ConstraintLayout") return true
        return false
    }

    /**
     * Returns true if the tag is a Guideline or another excluded widget type.
     */
    private fun isExcludedTag(tag: String): Boolean {
        if (tag in EXCLUDED_TAGS) return true
        if (tag in EXCLUDED_SIMPLE_TAGS) return true
        // Check simple name
        val simpleName = tag.substringAfterLast('.')
        if (simpleName in EXCLUDED_SIMPLE_TAGS) return true
        return false
    }

    /**
     * Checks for horizontal constraints using prefix-based detection for
     * any constraint attribute that implies horizontal positioning.
     */
    private fun hasHorizontalConstraintInAttrs(attrNames: Set<String>): Boolean {
        return attrNames.any { attr ->
            attr.startsWith("layout_constraint") &&
                (attr.contains("Left") || attr.contains("Right") ||
                    attr.contains("Start") || attr.contains("End") ||
                    (attr.contains("Horizontal") && !attr.contains("Vertical")))
        }
    }

    /**
     * Checks for vertical constraints using prefix-based detection for
     * any constraint attribute that implies vertical positioning.
     */
    private fun hasVerticalConstraintInAttrs(attrNames: Set<String>): Boolean {
        return attrNames.any { attr ->
            attr.startsWith("layout_constraint") &&
                (attr.contains("Top") || attr.contains("Bottom") ||
                    attr.contains("Baseline") ||
                    (attr.contains("Vertical") && !attr.contains("Horizontal")))
        }
    }
}
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
import com.android.SdkConstants.TAG_INCLUDE
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
 * Checks that widgets inside a ConstraintLayout have proper constraints.
 */
class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        /** The main issue discovered by this detector */
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

        // Constraint attribute prefixes in the app namespace
        private const val ATTR_LAYOUT_CONSTRAINT_LEFT_TO_LEFT_OF = "layout_constraintLeft_toLeftOf"
        private const val ATTR_LAYOUT_CONSTRAINT_LEFT_TO_RIGHT_OF = "layout_constraintLeft_toRightOf"
        private const val ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_LEFT_OF = "layout_constraintRight_toLeftOf"
        private const val ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_RIGHT_OF = "layout_constraintRight_toRightOf"
        private const val ATTR_LAYOUT_CONSTRAINT_TOP_TO_TOP_OF = "layout_constraintTop_toTopOf"
        private const val ATTR_LAYOUT_CONSTRAINT_TOP_TO_BOTTOM_OF = "layout_constraintTop_toBottomOf"
        private const val ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_TOP_OF = "layout_constraintBottom_toTopOf"
        private const val ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_BOTTOM_OF = "layout_constraintBottom_toBottomOf"
        private const val ATTR_LAYOUT_CONSTRAINT_START_TO_START_OF = "layout_constraintStart_toStartOf"
        private const val ATTR_LAYOUT_CONSTRAINT_START_TO_END_OF = "layout_constraintStart_toEndOf"
        private const val ATTR_LAYOUT_CONSTRAINT_END_TO_START_OF = "layout_constraintEnd_toStartOf"
        private const val ATTR_LAYOUT_CONSTRAINT_END_TO_END_OF = "layout_constraintEnd_toEndOf"
        private const val ATTR_LAYOUT_CONSTRAINT_BASELINE_TO_BASELINE_OF =
            "layout_constraintBaseline_toBaselineOf"
        private const val ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle"

        // Horizontal constraint attributes
        private val HORIZONTAL_CONSTRAINT_ATTRS = setOf(
            ATTR_LAYOUT_CONSTRAINT_LEFT_TO_LEFT_OF,
            ATTR_LAYOUT_CONSTRAINT_LEFT_TO_RIGHT_OF,
            ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_LEFT_OF,
            ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_RIGHT_OF,
            ATTR_LAYOUT_CONSTRAINT_START_TO_START_OF,
            ATTR_LAYOUT_CONSTRAINT_START_TO_END_OF,
            ATTR_LAYOUT_CONSTRAINT_END_TO_START_OF,
            ATTR_LAYOUT_CONSTRAINT_END_TO_END_OF
        )

        // Vertical constraint attributes
        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            ATTR_LAYOUT_CONSTRAINT_TOP_TO_TOP_OF,
            ATTR_LAYOUT_CONSTRAINT_TOP_TO_BOTTOM_OF,
            ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_TOP_OF,
            ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_BOTTOM_OF
        )

        // Tags that should be skipped (they don't need constraints)
        private val SKIP_TAGS = setOf(
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Guideline",
            TAG_INCLUDE,
            "requestFocus",
            "Space",
            "android.support.v4.widget.Space"
        )

        // Attribute for editor absolute positions (designtime only)
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        // Fully qualified ConstraintLayout class names
        private val CONSTRAINT_LAYOUT_CLASSES = setOf(
            CLASS_CONSTRAINT_LAYOUT.newName(),
            CLASS_CONSTRAINT_LAYOUT.oldName(),
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parentNode = element.parentNode as? Element ?: return

        // Check if the parent is a ConstraintLayout
        if (!isConstraintLayout(parentNode)) {
            return
        }

        val tag = element.tagName

        // Skip tags that don't need constraints
        if (tag in SKIP_TAGS) {
            return
        }

        // Skip Guideline (fully qualified names)
        if (tag.endsWith("Guideline")) {
            return
        }

        // Skip include tags
        if (tag == TAG_INCLUDE) {
            return
        }

        // Check if the child has match_parent for width/height (which is unusual in CL but valid)
        // match_parent children are sometimes treated as if constrained
        val layoutWidth = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val layoutHeight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        // If match_parent is used, we won't flag it (though it's not recommended in ConstraintLayout)
        if (layoutWidth == VALUE_MATCH_PARENT && layoutHeight == VALUE_MATCH_PARENT) {
            return
        }

        // Check for circle constraint (handles both horizontal and vertical)
        if (element.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_CONSTRAINT_CIRCLE)) {
            return
        }

        // Check for baseline constraint (this handles vertical positioning)
        val hasBaseline = element.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_CONSTRAINT_BASELINE_TO_BASELINE_OF)

        // Check horizontal constraints
        val hasHorizontalConstraint = layoutWidth == VALUE_MATCH_PARENT ||
            HORIZONTAL_CONSTRAINT_ATTRS.any { element.hasAttributeNS(AUTO_URI, it) }

        // Check vertical constraints
        val hasVerticalConstraint = layoutHeight == VALUE_MATCH_PARENT ||
            hasBaseline ||
            VERTICAL_CONSTRAINT_ATTRS.any { element.hasAttributeNS(AUTO_URI, it) }

        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            // Check if it has editor-only attributes (which means it was placed in the editor
            // but not properly constrained)
            val hasEditorAbsoluteX = element.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)
            val hasEditorAbsoluteY = element.hasAttributeNS(AUTO_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)

            // Only report if the widget has no constraints at all, or if it only has
            // editor absolute position attributes
            val missingHorizontal = !hasHorizontalConstraint
            val missingVertical = !hasVerticalConstraint

            if (missingHorizontal || missingVertical) {
                val message = buildMissingConstraintsMessage(missingHorizontal, missingVertical)
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    message
                )
            }
        }
    }

    /**
     * Returns true if the given element is a ConstraintLayout.
     */
    private fun isConstraintLayout(element: Element): Boolean {
        val tag = element.tagName
        return tag in CONSTRAINT_LAYOUT_CLASSES ||
            tag == "android.support.constraint.ConstraintLayout" ||
            tag == "androidx.constraintlayout.widget.ConstraintLayout" ||
            tag.endsWith("ConstraintLayout")
    }

    /**
     * Builds a message describing the missing constraints.
     */
    private fun buildMissingConstraintsMessage(missingHorizontal: Boolean, missingVertical: Boolean): String {
        return when {
            missingHorizontal && missingVertical ->
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
            missingHorizontal ->
                "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
            else ->
                "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
        }
    }
}
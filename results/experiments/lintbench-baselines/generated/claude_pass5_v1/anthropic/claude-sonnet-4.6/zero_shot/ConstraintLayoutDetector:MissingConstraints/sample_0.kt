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
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE
import com.android.SdkConstants.FQCN_CONSTRAINT_LAYOUT
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
 * Checks that all children of a ConstraintLayout have at least one
 * horizontal and one vertical constraint.
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

        // Constraint layout namespace prefixes and attributes
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
        private const val ATTR_LAYOUT_CENTER_X_BIAS = "layout_constraintHorizontal_bias"
        private const val ATTR_LAYOUT_CENTER_Y_BIAS = "layout_constraintVertical_bias"

        private val HORIZONTAL_CONSTRAINT_ATTRS = setOf(
            ATTR_LAYOUT_LEFT_TO_LEFT_OF,
            ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
            ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
            ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
            ATTR_LAYOUT_START_TO_START_OF,
            ATTR_LAYOUT_START_TO_END_OF,
            ATTR_LAYOUT_END_TO_START_OF,
            ATTR_LAYOUT_END_TO_END_OF,
            ATTR_LAYOUT_CENTER_X_BIAS
        )

        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            ATTR_LAYOUT_TOP_TO_TOP_OF,
            ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
            ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
            ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
            ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
            ATTR_LAYOUT_CENTER_Y_BIAS
        )

        /**
         * Tags that are not real view widgets and should be skipped
         * when checking for constraints.
         */
        private val SKIP_TAGS = setOf(
            "requestFocus",
            "tag",
            "include"
        )

        /**
         * Simple class names (without package) of widgets that are
         * exempt from constraint checks (e.g. Guideline, Barrier).
         */
        private val EXEMPT_SIMPLE_NAMES = setOf(
            "Guideline",
            "Barrier",
            "Group",
            "Placeholder",
            "MockView",
            "ConstraintHelper"
        )

        /**
         * Fully qualified class names that are exempt from constraint checks.
         */
        private val EXEMPT_FQ_NAMES = setOf(
            CLASS_CONSTRAINT_LAYOUT_GUIDELINE,
            CLASS_CONSTRAINT_LAYOUT_BARRIER,
            "androidx.constraintlayout.widget.Group",
            "androidx.constraintlayout.widget.Placeholder",
            "com.android.tools.sherpa.interaction.MockView"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        // We want to visit ConstraintLayout elements
        return listOf(
            CLASS_CONSTRAINT_LAYOUT.newName(),
            CLASS_CONSTRAINT_LAYOUT.oldName(),
            FQCN_CONSTRAINT_LAYOUT
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName

        // Verify this is actually a ConstraintLayout
        if (!isConstraintLayout(tagName)) {
            return
        }

        // Check each child of the ConstraintLayout
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) {
                continue
            }

            checkChild(context, child)
        }
    }

    private fun isConstraintLayout(tagName: String): Boolean {
        return tagName == CLASS_CONSTRAINT_LAYOUT.newName() ||
                tagName == CLASS_CONSTRAINT_LAYOUT.oldName() ||
                tagName == FQCN_CONSTRAINT_LAYOUT ||
                tagName.endsWith(".ConstraintLayout")
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tag = child.tagName

        // Skip non-view elements
        if (SKIP_TAGS.contains(tag)) {
            return
        }

        // Skip exempt widget types (Guideline, Barrier, etc.)
        if (isExemptWidget(tag)) {
            return
        }

        // If the widget uses match_parent for width or height, those dimensions
        // don't need constraints in that direction
        val layoutWidth = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val layoutHeight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        val widthIsMatchParent = layoutWidth == VALUE_MATCH_PARENT
        val heightIsMatchParent = layoutHeight == VALUE_MATCH_PARENT

        var hasHorizontalConstraint = widthIsMatchParent
        var hasVerticalConstraint = heightIsMatchParent

        // Check all attributes for constraint definitions in the app namespace
        val attributes = child.attributes
        for (j in 0 until attributes.length) {
            val attr = attributes.item(j)
            val ns = attr.namespaceURI ?: continue
            if (ns != AUTO_URI && ns != "http://schemas.android.com/apk/res-auto") {
                continue
            }
            val localName = attr.localName ?: continue

            if (!hasHorizontalConstraint && HORIZONTAL_CONSTRAINT_ATTRS.contains(localName)) {
                hasHorizontalConstraint = true
            }
            if (!hasVerticalConstraint && VERTICAL_CONSTRAINT_ATTRS.contains(localName)) {
                hasVerticalConstraint = true
            }

            if (hasHorizontalConstraint && hasVerticalConstraint) {
                break
            }
        }

        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            val id = child.getAttributeNS(ANDROID_URI, ATTR_ID)
            val idString = if (id.isNotEmpty()) "`$id`" else "This view"

            val message = when {
                !hasHorizontalConstraint && !hasVerticalConstraint ->
                    "$idString is not constrained horizontally and vertically: " +
                            "at runtime it will jump to (0,0) unless you add the constraints"
                !hasHorizontalConstraint ->
                    "$idString is not constrained horizontally: " +
                            "at runtime it will jump to the left unless you add a horizontal constraint"
                else ->
                    "$idString is not constrained vertically: " +
                            "at runtime it will jump to the top unless you add a vertical constraint"
            }

            context.report(
                ISSUE,
                child,
                context.getNameLocation(child),
                message
            )
        }
    }

    private fun isExemptWidget(tag: String): Boolean {
        // Check fully qualified names
        if (EXEMPT_FQ_NAMES.contains(tag)) {
            return true
        }

        // Check simple names (last component after dot)
        val simpleName = if (tag.contains('.')) tag.substringAfterLast('.') else tag
        if (EXEMPT_SIMPLE_NAMES.contains(simpleName)) {
            return true
        }

        return false
    }
}
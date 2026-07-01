package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks that widgets inside a ConstraintLayout have both horizontal
 * and vertical constraints defined.
 */
class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private const val CONSTRAINT_LAYOUT = "ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_FQCN = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_FQCN_OLD = "android.support.constraint.ConstraintLayout"

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
            "layout_constraintWidth_percent",
            "layout_constraintWidth_default"
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
            "layout_constraintHeight_percent",
            "layout_constraintHeight_default"
        )

        // Attributes that indicate the widget is a guideline or barrier (no constraints needed)
        private val SKIP_TAGS = setOf(
            "Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Barrier",
            "Group",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group",
            TAG_INCLUDE,
            "requestFocus",
            "tag",
            "androidx.constraintlayout.widget.Placeholder",
            "android.support.constraint.Placeholder",
            "Flow",
            "androidx.constraintlayout.widget.helper.Flow",
            "Layer",
            "androidx.constraintlayout.widget.Layer"
        )

        // Attributes that indicate the widget is a chain head or has special positioning
        private const val ATTR_LAYOUT_CHAIN_PACKED = "layout_constraintHorizontal_chainStyle"
        private const val ATTR_LAYOUT_VERTICAL_CHAIN_STYLE = "layout_constraintVertical_chainStyle"

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

        private fun isConstraintLayout(tagName: String): Boolean {
            return tagName == CONSTRAINT_LAYOUT ||
                tagName == CONSTRAINT_LAYOUT_FQCN ||
                tagName == CONSTRAINT_LAYOUT_FQCN_OLD ||
                tagName.endsWith(".$CONSTRAINT_LAYOUT")
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            CONSTRAINT_LAYOUT,
            CONSTRAINT_LAYOUT_FQCN,
            CONSTRAINT_LAYOUT_FQCN_OLD
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element.tagName)) {
            return
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) continue

            val tagName = child.tagName
            if (tagName in SKIP_TAGS) continue

            // Check if the child has a "tools:ignore" for this issue
            val toolsIgnore = child.getAttributeNS(SdkConstants.TOOLS_URI, "ignore")
            if (toolsIgnore.contains("MissingConstraints")) continue

            // Check for match_parent which implicitly provides constraints
            val layoutWidth = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
            val layoutHeight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

            val hasHorizontalConstraint = layoutWidth == "match_parent" ||
                hasAnyAttribute(child, HORIZONTAL_CONSTRAINT_ATTRS)

            val hasVerticalConstraint = layoutHeight == "match_parent" ||
                hasAnyAttribute(child, VERTICAL_CONSTRAINT_ATTRS)

            if (!hasHorizontalConstraint || !hasVerticalConstraint) {
                val id = child.getAttributeNS(ANDROID_URI, ATTR_ID)
                val widgetName = if (id.isNotEmpty()) {
                    val idValue = id.removePrefix("@+id/").removePrefix("@id/")
                    "`$idValue`"
                } else {
                    "This view"
                }

                val missing = when {
                    !hasHorizontalConstraint && !hasVerticalConstraint ->
                        "a horizontal and a vertical constraint"
                    !hasHorizontalConstraint -> "a horizontal constraint"
                    else -> "a vertical constraint"
                }

                context.report(
                    ISSUE,
                    child,
                    context.getNameLocation(child),
                    "$widgetName is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
                        .takeIf { hasHorizontalConstraint }
                        ?: ("$widgetName is not constrained horizontally: at runtime it will jump to the left side unless you add a horizontal constraint"
                            .takeIf { hasVerticalConstraint }
                            ?: "$widgetName is missing $missing")
                )
            }
        }
    }

    private fun hasAnyAttribute(element: Element, attributeNames: Set<String>): Boolean {
        val attrs = element.attributes ?: return false
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val localName = attr.localName ?: attr.nodeName
            if (localName in attributeNames) {
                val ns = attr.namespaceURI
                if (ns == AUTO_URI || ns == ANDROID_URI || ns == null) {
                    return true
                }
            }
        }
        return false
    }
}
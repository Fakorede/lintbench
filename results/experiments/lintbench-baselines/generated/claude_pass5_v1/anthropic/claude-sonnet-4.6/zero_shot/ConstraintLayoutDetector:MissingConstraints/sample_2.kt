package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT
import com.android.SdkConstants.TOOLS_URI
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
            "layout_constraintBaseline_toTopOf",
            "layout_constraintBaseline_toBottomOf",
            "layout_constraintVertical_bias"
        )

        // Tags that don't need constraints
        private val EXCLUDED_TAGS = setOf(
            "Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "Group",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group",
            "Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Barrier",
            "Layer",
            "androidx.constraintlayout.widget.Layer",
            "android.support.constraint.Layer",
            "Flow",
            "androidx.constraintlayout.widget.helper.Flow",
            "androidx.constraintlayout.helper.widget.Flow"
        )

        // ConstraintLayout class names
        private val CONSTRAINT_LAYOUT_CLASSES = setOf(
            CLASS_CONSTRAINT_LAYOUT,
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parentElement = element.parentNode as? Element ?: return

        // Check if parent is a ConstraintLayout
        val parentTag = parentElement.tagName
        if (!CONSTRAINT_LAYOUT_CLASSES.contains(parentTag)) {
            return
        }

        val tag = element.tagName

        // Skip excluded tags (Guideline, Group, Barrier, etc.)
        if (EXCLUDED_TAGS.contains(tag)) {
            return
        }

        // Check if element has layout_width and layout_height (it's a real view)
        val hasLayoutWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val hasLayoutHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        if (!hasLayoutWidth && !hasLayoutHeight) {
            // Not a typical view child
            return
        }

        // Check for match_parent / fill_parent on width/height which implies constraints
        val layoutWidth = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val layoutHeight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        // If width is match_parent, it has implicit horizontal constraints
        val hasImplicitHorizontal = layoutWidth == "match_parent" || layoutWidth == "fill_parent"
        val hasImplicitVertical = layoutHeight == "match_parent" || layoutHeight == "fill_parent"

        var hasHorizontalConstraint = hasImplicitHorizontal
        var hasVerticalConstraint = hasImplicitVertical

        // Check all attributes of the element for constraint attributes
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val attrName = attr.localName ?: continue
            val attrNamespace = attr.namespaceURI ?: continue

            if (attrNamespace != AUTO_URI && attrNamespace != "http://schemas.android.com/apk/res-auto") {
                continue
            }

            if (!hasHorizontalConstraint && HORIZONTAL_CONSTRAINT_ATTRS.contains(attrName)) {
                hasHorizontalConstraint = true
            }
            if (!hasVerticalConstraint && VERTICAL_CONSTRAINT_ATTRS.contains(attrName)) {
                hasVerticalConstraint = true
            }

            if (hasHorizontalConstraint && hasVerticalConstraint) {
                break
            }
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
                scope = element,
                location = context.getNameLocation(element),
                message = "This view is not constrained. It only has designtime positions, " +
                        "so it will jump to (0,0) at runtime unless you add the constraints. " +
                        "It is missing $missing"
            )
        }
    }
}
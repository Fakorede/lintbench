package com.android.tools.lint.checks

import com.android.tools.lint.client.api.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.CLASS_CONSTRAINT_LAYOUT, SdkConstants.CLASS_CONSTRAINT_LAYOUT_V7)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val tag = child.tagName
            if (tag == SdkConstants.TAG_INCLUDE ||
                tag == SdkConstants.TAG_MERGE ||
                tag == SdkConstants.TAG_REQUEST_FOCUS ||
                isHelper(tag)
            ) {
                continue
            }

            val hasHorizontal = hasHorizontalConstraint(child)
            val hasVertical = hasVerticalConstraint(child)

            if (!hasHorizontal || !hasVertical) {
                val message = when {
                    !hasHorizontal && !hasVertical ->
                        "This view is not constrained horizontally or vertically"
                    !hasHorizontal ->
                        "This view is not constrained horizontally"
                    else ->
                        "This view is not constrained vertically"
                }
                context.report(ISSUE, child, context.getNameLocation(child), message)
            }
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean =
        HORIZONTAL_CONSTRAINTS.any { element.hasAttributeNS(SdkConstants.AUTO_URI, it) }

    private fun hasVerticalConstraint(element: Element): Boolean =
        VERTICAL_CONSTRAINTS.any { element.hasAttributeNS(SdkConstants.AUTO_URI, it) }

    private fun isHelper(tag: String): Boolean {
        return tag.endsWith(".Guideline") ||
            tag.endsWith(".Barrier") ||
            tag.endsWith(".Group") ||
            tag.endsWith(".Placeholder") ||
            tag.endsWith(".Layer") ||
            tag.endsWith(".Flow") ||
            tag.endsWith(".Carousel") ||
            tag.endsWith(".ConstraintHelper")
    }

    companion object {
        private val HORIZONTAL_CONSTRAINTS = arrayOf(
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_LEFT_TO_LEFT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_LEFT_TO_RIGHT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_LEFT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_RIGHT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_START_TO_START_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_START_TO_END_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_END_TO_START_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_END_TO_END_OF
        )

        private val VERTICAL_CONSTRAINTS = arrayOf(
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_TOP_TO_TOP_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_TOP_TO_BOTTOM_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_TOP_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_BOTTOM_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_BASELINE_TO_BASELINE_OF
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            "MissingConstraints",
            "Missing Constraints in ConstraintLayout",
            """
                The layout editor allows you to place widgets anywhere on the canvas, and it records
                the current position with designtime attributes (such as `layout_editor_absoluteX`).
                These attributes are **not** applied at runtime, so if you push your layout on a
                device, the widgets may appear in a different location than shown in the editor.
                To fix this, make sure a widget has both horizontal and vertical constraints by
                dragging from the edge connections.
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
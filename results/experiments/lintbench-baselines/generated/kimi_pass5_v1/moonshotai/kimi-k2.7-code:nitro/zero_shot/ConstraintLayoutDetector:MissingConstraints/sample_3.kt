package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.SdkConstants.TOOLS_URI
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> = CONSTRAINT_LAYOUT_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element)) {
            return
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (isGuideline(child)) {
                continue
            }
            checkChild(context, child)
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val hasAbsoluteX = hasDesignPosition(child, ATTR_LAYOUT_EDITOR_ABSOLUTE_X)
        val hasAbsoluteY = hasDesignPosition(child, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)
        if (!hasAbsoluteX && !hasAbsoluteY) {
            return
        }

        val hasHorizontal = HORIZONTAL_CONSTRAINT_ATTRIBUTES.any {
            child.hasAttributeNS(AUTO_URI, it)
        }
        val hasVertical = VERTICAL_CONSTRAINT_ATTRIBUTES.any {
            child.hasAttributeNS(AUTO_URI, it)
        }

        val location = context.getLocation(child)
        if (hasAbsoluteX && !hasHorizontal) {
            context.report(
                ISSUE,
                child,
                location,
                "This view is not constrained horizontally: only designtime positions are set (such as `layout_editor_absoluteX`)."
            )
        }
        if (hasAbsoluteY && !hasVertical) {
            context.report(
                ISSUE,
                child,
                location,
                "This view is not constrained vertically: only designtime positions are set (such as `layout_editor_absoluteY`)."
            )
        }
    }

    private fun hasDesignPosition(element: Element, attributeName: String): Boolean {
        return element.hasAttributeNS(TOOLS_URI, attributeName) ||
                element.hasAttributeNS(AUTO_URI, attributeName)
    }

    private fun isConstraintLayout(element: Element): Boolean {
        val tag = element.tagName
        return tag == ANDROIDX_CONSTRAINT_LAYOUT ||
                tag == SUPPORT_CONSTRAINT_LAYOUT ||
                tag.endsWith("ConstraintLayout")
    }

    private fun isGuideline(element: Element): Boolean {
        val tag = element.tagName
        return tag == ANDROIDX_GUIDELINE ||
                tag == SUPPORT_GUIDELINE ||
                tag.endsWith("Guideline")
    }

    companion object {
        private const val ANDROIDX_CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val SUPPORT_CONSTRAINT_LAYOUT = "android.support.constraint.ConstraintLayout"
        private const val ANDROIDX_GUIDELINE = "androidx.constraintlayout.widget.Guideline"
        private const val SUPPORT_GUIDELINE = "android.support.constraint.Guideline"

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        private val CONSTRAINT_LAYOUT_TAGS = listOf(
            ANDROIDX_CONSTRAINT_LAYOUT,
            SUPPORT_CONSTRAINT_LAYOUT
        )

        private val HORIZONTAL_CONSTRAINT_ATTRIBUTES = listOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintCircle"
        )

        private val VERTICAL_CONSTRAINT_ATTRIBUTES = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintCircle"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are not applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
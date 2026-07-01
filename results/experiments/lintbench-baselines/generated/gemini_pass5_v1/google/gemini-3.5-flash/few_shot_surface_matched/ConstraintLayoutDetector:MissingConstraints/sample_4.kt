package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("*")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (!parentTag.endsWith("ConstraintLayout")) {
            return
        }

        val tagName = element.tagName
        if (tagName.endsWith("Guideline") ||
            tagName.endsWith("Barrier") ||
            tagName.endsWith("Group") ||
            tagName.endsWith("Placeholder")) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        // Check horizontal constraints
        if (element.hasAttributeNS(AUTO_URI, "layout_constraintLeft_toLeftOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintLeft_toRightOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintRight_toLeftOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintRight_toRightOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintStart_toStartOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintStart_toEndOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintEnd_toStartOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintEnd_toEndOf")) {
            hasHorizontal = true
        }

        // Check vertical constraints
        if (element.hasAttributeNS(AUTO_URI, "layout_constraintTop_toTopOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintTop_toBottomOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintBottom_toTopOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintBottom_toBottomOf") ||
            element.hasAttributeNS(AUTO_URI, "layout_constraintBaseline_toBaselineOf")) {
            hasVertical = true
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = when {
                !hasHorizontal && !hasVertical -> "horizontal and vertical"
                !hasHorizontal -> "horizontal"
                else -> "vertical"
            }
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the $missing constraints"
            )
        }
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.LAYOUT_SCOPE
            )
        )
    }
}
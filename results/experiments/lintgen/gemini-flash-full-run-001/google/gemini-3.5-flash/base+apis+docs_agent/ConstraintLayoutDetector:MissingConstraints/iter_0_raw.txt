package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ConstraintLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
            parentTag != "android.support.constraint.ConstraintLayout") {
            return
        }

        val tag = element.tagName
        if (tag.endsWith("Guideline") || tag.endsWith("Barrier") || tag.endsWith("Group") ||
            tag.endsWith("Placeholder") || tag.endsWith("Flow") || tag.endsWith("Layer") ||
            tag == "merge") {
            return
        }

        val autoNamespace = "http://schemas.android.com/apk/res-auto"
        val androidNamespace = "http://schemas.android.com/apk/res/android"

        val hasHorizontal = hasHorizontalConstraint(element, autoNamespace, androidNamespace)
        val hasVertical = hasVerticalConstraint(element, autoNamespace, androidNamespace)

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical ->
                    "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
                !hasHorizontal ->
                    "This view is not constrained horizontally: at runtime it will jump to the left."
                else ->
                    "This view is not constrained vertically: at runtime it will jump to the top."
            }
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                message
            )
        }
    }

    private fun hasHorizontalConstraint(element: Element, autoNS: String, androidNS: String): Boolean {
        val width = element.getAttributeNS(androidNS, "layout_width")
        if (width == "match_parent") return true

        val horizontalAttrs = listOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )
        for (attr in horizontalAttrs) {
            if (element.hasAttributeNS(autoNS, attr)) {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraint(element: Element, autoNS: String, androidNS: String): Boolean {
        val height = element.getAttributeNS(androidNS, "layout_height")
        if (height == "match_parent") return true

        val verticalAttrs = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
        )
        for (attr in verticalAttrs) {
            if (element.hasAttributeNS(autoNS, attr)) {
                return true
            }
        }
        return false
    }

    companion object {
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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
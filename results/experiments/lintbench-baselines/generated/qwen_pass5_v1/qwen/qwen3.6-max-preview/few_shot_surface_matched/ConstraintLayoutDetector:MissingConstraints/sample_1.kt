package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CONSTRAINT_LAYOUT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return Detector.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (parentTag != CONSTRAINT_LAYOUT && parentTag != "android.support.constraint.ConstraintLayout") {
            return
        }

        val tag = element.tagName
        if (tag.startsWith("androidx.constraintlayout.") || tag.startsWith("android.support.constraint.")) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.namespaceURI != AUTO_URI) continue
            val name = attr.localName
            if (!name.startsWith("layout_constraint")) continue

            when {
                name.endsWith("_toLeftOf") || name.endsWith("_toRightOf") ||
                name.endsWith("_toStartOf") || name.endsWith("_toEndOf") ||
                name == "layout_constraintHorizontal_bias" || name == "layout_constraintCircle" -> hasHorizontal = true

                name.endsWith("_toTopOf") || name.endsWith("_toBottomOf") ||
                name == "layout_constraintVertical_bias" || name == "layout_constraintCircle" ||
                name == "layout_constraintBaseline_toBaselineOf" -> hasVertical = true
            }

            if (hasHorizontal && hasVertical) return
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = when {
                !hasHorizontal && !hasVertical -> "horizontally or vertically"
                !hasHorizontal -> "horizontally"
                else -> "vertically"
            }
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained $missing. It only has designtime positions, " +
                "so it will jump to (0,0) at runtime unless you add the constraints"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
                "and it records the current position with designtime attributes (such as " +
                "`layout_editor_absoluteX`). These attributes are **not** applied at runtime, " +
                "so if you push your layout on a device, the widgets may appear in a different " +
                "location than shown in the editor. To fix this, make sure a widget has both " +
                "horizontal and vertical constraints by dragging from the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.NEW_CONSTRAINT_LAYOUT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return Detector.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (parentTag != CONSTRAINT_LAYOUT && parentTag != NEW_CONSTRAINT_LAYOUT) {
            return
        }

        val tag = element.tagName.substringAfterLast('.')
        if (tag in SKIPPED_VIEWS) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName
            if (!name.startsWith("layout_constraint")) continue

            if (name.contains("Left_to") || name.contains("Right_to") ||
                name.contains("Start_to") || name.contains("End_to") ||
                name.contains("Circle")) {
                hasHorizontal = true
            }
            if (name.contains("Top_to") || name.contains("Bottom_to") ||
                name.contains("Baseline_to") || name.contains("Circle")) {
                hasVertical = true
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = buildString {
                if (!hasHorizontal) append("horizontal")
                if (!hasHorizontal && !hasVertical) append(" and ")
                if (!hasVertical) append("vertical")
            }
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained. It only has designtime positions, so it will jump to ($missing) position at runtime unless you add the constraints"
            )
        }
    }

    companion object {
        private val SKIPPED_VIEWS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder",
            "Flow", "MockView", "ConstraintLayout"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
                "and it records the current position with designtime attributes (such as " +
                "`layout_editor_absoluteX`). These attributes are **not** applied at " +
                "runtime, so if you push your layout on a device, the widgets may appear " +
                "in a different location than shown in the editor. To fix this, make sure " +
                "a widget has both horizontal and vertical constraints by dragging from " +
                "the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
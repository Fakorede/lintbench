package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.VALUE_MATCH_PARENT
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

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
            parentTag != "android.support.constraint.ConstraintLayout") {
            return
        }

        val tag = element.tagName
        if (tag.endsWith(".Guideline") || tag.endsWith(".Barrier") ||
            tag.endsWith(".Group") || tag.endsWith(".Placeholder") ||
            tag.endsWith(".Flow") || tag.endsWith(".MockView")) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName ?: continue
            if (!name.startsWith("layout_constraint")) continue

            if (name.contains("Left") || name.contains("Right") ||
                name.contains("Start") || name.contains("End") ||
                name.contains("Circle") || name.contains("HorizontalBias") ||
                name.contains("WidthPercent") || name.contains("WidthDefault")) {
                hasHorizontal = true
            }
            if (name.contains("Top") || name.contains("Bottom") ||
                name.contains("Baseline") || name.contains("Circle") ||
                name.contains("VerticalBias") || name.contains("HeightPercent") ||
                name.contains("HeightDefault")) {
                hasVertical = true
            }
        }

        if (element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH) == VALUE_MATCH_PARENT) {
            hasHorizontal = true
        }
        if (element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT) == VALUE_MATCH_PARENT) {
            hasVertical = true
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
                "This view is not constrained. It only has designtime positions, so it will " +
                    "jump to (0,0) at runtime unless you add $missing constraints"
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
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.TAG_MERGE
import com.android.SdkConstants.VALUE_MATCH_PARENT
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
        if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
            parentTag != "android.support.constraint.ConstraintLayout") {
            return
        }

        val tag = element.tagName
        if (tag == "androidx.constraintlayout.widget.Guideline" ||
            tag == "androidx.constraintlayout.widget.Barrier" ||
            tag == "androidx.constraintlayout.widget.Group" ||
            tag == "androidx.constraintlayout.widget.Placeholder" ||
            tag == "androidx.constraintlayout.widget.ReactiveGuide" ||
            tag == "Fragment" ||
            tag == TAG_INCLUDE ||
            tag == TAG_MERGE) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName
            if (name.startsWith("layout_constraint")) {
                when {
                    name.contains("Left_") || name.contains("Right_") ||
                    name.contains("Start_") || name.contains("End_") -> hasHorizontal = true
                    name.contains("Top_") || name.contains("Bottom_") ||
                    name.contains("Baseline_") -> hasVertical = true
                    name == "layout_constraintCircle" -> {
                        hasHorizontal = true
                        hasVertical = true
                    }
                }
            }
        }

        val width = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        if (width == VALUE_MATCH_PARENT || width == "fill_parent") hasHorizontal = true
        val height = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
        if (height == VALUE_MATCH_PARENT || height == "fill_parent") hasVertical = true

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
                "This view is not constrained $missing. At runtime, it will jump to the top-left unless you add constraints."
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
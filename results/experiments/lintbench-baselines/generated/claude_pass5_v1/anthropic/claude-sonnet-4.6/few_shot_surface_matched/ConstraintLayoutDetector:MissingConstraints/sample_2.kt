package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.FQCN_CONSTRAINT_LAYOUT
import com.android.SdkConstants.TAG_INCLUDE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            FQCN_CONSTRAINT_LAYOUT,
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            checkChild(context, child)
            child = XmlUtils.getNextTag(child)
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tag = element.tagName
        if (tag == TAG_INCLUDE || tag == "requestFocus" || tag == "tag" || tag == "Space" ||
            tag == "android.widget.Space"
        ) {
            return
        }

        // Skip if it's a guideline
        if (tag.endsWith("Guideline")) {
            return
        }

        // Skip if the element has no id and is invisible (e.g. 0dp x 0dp)
        val visibility = element.getAttributeNS(ANDROID_URI, "visibility")
        if (visibility == "gone") {
            return
        }

        val hasHorizontalConstraint = hasHorizontalConstraint(element)
        val hasVerticalConstraint = hasVerticalConstraint(element)

        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            val id = element.getAttributeNS(ANDROID_URI, ATTR_ID)
            val displayId = if (id.isNotEmpty()) {
                "`${id.removePrefix("@+id/").removePrefix("@id/")}` "
            } else {
                ""
            }

            val missing = when {
                !hasHorizontalConstraint && !hasVerticalConstraint ->
                    "horizontal and vertical constraints"
                !hasHorizontalConstraint -> "a horizontal constraint"
                else -> "a vertical constraint"
            }

            val location = context.getNameLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "This view is not constrained. It only has designtime positions, so it will " +
                    "jump to (0,0) at runtime unless you add the constraints: " +
                    "Widget ${displayId}is missing $missing"
            )
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        // Check for any of the horizontal constraint attributes in the app (auto) namespace
        val autoNs = AUTO_URI
        return element.hasAttributeNS(autoNs, "layout_constraintLeft_toLeftOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintLeft_toRightOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintRight_toLeftOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintRight_toRightOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintStart_toStartOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintStart_toEndOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintEnd_toStartOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintEnd_toEndOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintHorizontal_bias") ||
            element.hasAttributeNS(autoNs, "layout_constraintCircle") ||
            // Check layout_centerHorizontally equivalent in ConstraintLayout (not standard, but be safe)
            element.hasAttributeNS(autoNs, "layout_constraintLeft_creator") ||
            element.hasAttributeNS(autoNs, "layout_constraintRight_creator")
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        val autoNs = AUTO_URI
        return element.hasAttributeNS(autoNs, "layout_constraintTop_toTopOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintTop_toBottomOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintBottom_toTopOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintBottom_toBottomOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintBaseline_toBaselineOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintBaseline_toTopOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintBaseline_toBottomOf") ||
            element.hasAttributeNS(autoNs, "layout_constraintVertical_bias") ||
            element.hasAttributeNS(autoNs, "layout_constraintCircle") ||
            element.hasAttributeNS(autoNs, "layout_constraintTop_creator") ||
            element.hasAttributeNS(autoNs, "layout_constraintBottom_creator")
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation =
                """
                The layout editor allows you to place widgets anywhere on the canvas, and it \
                records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so \
                if you push your layout on a device, the widgets may appear in a different \
                location than shown in the editor. To fix this, make sure a widget has both \
                horizontal and vertical constraints by dragging from the edge connections.
                """,
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
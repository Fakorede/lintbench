package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
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
        private const val CONSTRAINT_LAYOUT = "ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_FQCN = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_FQCN_OLD = "android.support.constraint.ConstraintLayout"

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        // Tags that don't need constraints
        private val SKIP_TAGS = setOf(
            "Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Barrier",
            "Group",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group",
            "Flow",
            "androidx.constraintlayout.helper.widget.Flow",
            "Layer",
            "androidx.constraintlayout.helper.widget.Layer"
        )

        @JvmField
        val ISSUE = Issue.create(
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

        private fun isConstraintLayout(element: Element): Boolean {
            val tag = element.tagName
            return tag == CONSTRAINT_LAYOUT_FQCN ||
                tag == CONSTRAINT_LAYOUT_FQCN_OLD ||
                tag.endsWith(".$CONSTRAINT_LAYOUT") ||
                tag == CONSTRAINT_LAYOUT
        }

        private fun isSkipTag(tag: String): Boolean {
            if (tag in SKIP_TAGS) return true
            // Check simple name
            val simpleName = if (tag.contains('.')) tag.substringAfterLast('.') else tag
            return simpleName in setOf("Guideline", "Barrier", "Group", "Flow", "Layer")
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            CONSTRAINT_LAYOUT_FQCN,
            CONSTRAINT_LAYOUT_FQCN_OLD,
            "ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element)) return

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) continue

            val tag = child.tagName
            if (isSkipTag(tag)) continue

            // Check for horizontal constraints
            val hasHorizontalConstraint = hasHorizontalConstraint(child)
            // Check for vertical constraints
            val hasVerticalConstraint = hasVerticalConstraint(child)

            if (!hasHorizontalConstraint || !hasVerticalConstraint) {
                val missingHorizontal = !hasHorizontalConstraint
                val missingVertical = !hasVerticalConstraint

                val message = buildMessage(missingHorizontal, missingVertical)
                context.report(
                    ISSUE,
                    child,
                    context.getNameLocation(child),
                    message
                )
            }
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val localName = attr.localName ?: continue
            val ns = attr.namespaceURI ?: continue
            if ((ns == AUTO_URI || ns == SdkConstants.SHERPA_URI) &&
                isHorizontalConstraintAttr(localName)
            ) {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val localName = attr.localName ?: continue
            val ns = attr.namespaceURI ?: continue
            if ((ns == AUTO_URI || ns == SdkConstants.SHERPA_URI) &&
                isVerticalConstraintAttr(localName)
            ) {
                return true
            }
        }
        return false
    }

    private fun isHorizontalConstraintAttr(localName: String): Boolean {
        return localName.startsWith("layout_constraintLeft_") ||
            localName.startsWith("layout_constraintRight_") ||
            localName.startsWith("layout_constraintStart_") ||
            localName.startsWith("layout_constraintEnd_") ||
            localName == "layout_constraintHorizontal_bias" ||
            localName == "layout_constraintHorizontal_chainStyle" ||
            localName == "layout_constraintHorizontal_weight" ||
            localName == "layout_constraintWidth_percent" ||
            localName == "layout_constraintWidth_min" ||
            localName == "layout_constraintWidth_max" ||
            localName == "layout_constraintWidth_default"
    }

    private fun isVerticalConstraintAttr(localName: String): Boolean {
        return localName.startsWith("layout_constraintTop_") ||
            localName.startsWith("layout_constraintBottom_") ||
            localName.startsWith("layout_constraintBaseline_") ||
            localName == "layout_constraintVertical_bias" ||
            localName == "layout_constraintVertical_chainStyle" ||
            localName == "layout_constraintVertical_weight" ||
            localName == "layout_constraintHeight_percent" ||
            localName == "layout_constraintHeight_min" ||
            localName == "layout_constraintHeight_max" ||
            localName == "layout_constraintHeight_default"
    }

    private fun buildMessage(missingHorizontal: Boolean, missingVertical: Boolean): String {
        return when {
            missingHorizontal && missingVertical ->
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
            missingHorizontal ->
                "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
            missingVertical ->
                "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
            else -> ""
        }
    }
}
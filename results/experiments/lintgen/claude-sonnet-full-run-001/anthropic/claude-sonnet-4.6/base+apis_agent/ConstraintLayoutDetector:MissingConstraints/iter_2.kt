package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.AUTO_URI
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
        private const val CONSTRAINT_LAYOUT_FQCN = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_FQCN_OLD = "android.support.constraint.ConstraintLayout"

        // Tags that don't need constraints (they are helpers, not regular views)
        private val SKIP_SIMPLE_NAMES = setOf(
            "Guideline",
            "Barrier",
            "Group",
            "Flow",
            "Layer"
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
            return tag == CONSTRAINT_LAYOUT_FQCN || tag == CONSTRAINT_LAYOUT_FQCN_OLD
        }

        private fun shouldSkip(element: Element): Boolean {
            val tag = element.tagName
            val simpleName = if (tag.contains('.')) tag.substringAfterLast('.') else tag
            return simpleName in SKIP_SIMPLE_NAMES
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            CONSTRAINT_LAYOUT_FQCN,
            CONSTRAINT_LAYOUT_FQCN_OLD
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element)) return

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) continue

            if (shouldSkip(child)) continue

            val hasHorizontalConstraint = hasHorizontalConstraint(child)
            val hasVerticalConstraint = hasVerticalConstraint(child)

            if (!hasHorizontalConstraint || !hasVerticalConstraint) {
                val message = buildMessage(!hasHorizontalConstraint, !hasVerticalConstraint)
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
            if (isConstraintNamespace(ns) && isHorizontalConstraintAttr(localName)) {
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
            if (isConstraintNamespace(ns) && isVerticalConstraintAttr(localName)) {
                return true
            }
        }
        return false
    }

    private fun isConstraintNamespace(ns: String): Boolean {
        return ns == AUTO_URI || ns == SdkConstants.SHERPA_URI
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
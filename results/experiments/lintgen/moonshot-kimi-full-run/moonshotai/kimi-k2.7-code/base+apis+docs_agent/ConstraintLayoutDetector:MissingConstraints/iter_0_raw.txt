package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SdkConstants
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ConstraintLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        if (!parent.nodeName.endsWith("ConstraintLayout")) {
            return
        }

        if (isHelperWidget(element)) {
            return
        }

        val hasHorizontal = hasHorizontalConstraints(element)
        val hasVertical = hasVerticalConstraints(element)

        if (!hasHorizontal) {
            reportMissing(context, element, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "horizontally")
        }
        if (!hasVertical) {
            reportMissing(context, element, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "vertically")
        }
    }

    private fun isHelperWidget(element: Element): Boolean {
        val name = element.nodeName
        return name.endsWith("Guideline") || name.endsWith("Barrier") || name.endsWith("Group")
    }

    private fun hasHorizontalConstraints(element: Element): Boolean {
        return HORIZONTAL_CONSTRAINTS.any { element.hasAttributeNS(SdkConstants.AUTO_URI, it) }
    }

    private fun hasVerticalConstraints(element: Element): Boolean {
        return VERTICAL_CONSTRAINTS.any { element.hasAttributeNS(SdkConstants.AUTO_URI, it) }
    }

    private fun reportMissing(
        context: XmlContext,
        element: Element,
        editorAttrName: String,
        direction: String
    ) {
        val attr = element.getAttributeNodeNS(SdkConstants.TOOLS_URI, editorAttrName)
        val location = attr?.let { context.getLocation(it) } ?: context.getElementLocation(element)
        val adjective = when (direction) {
            "horizontally" -> "horizontal"
            "vertically" -> "vertical"
            else -> direction
        }
        val message = buildString {
            append("This view is not constrained $direction")
            if (attr != null) {
                append(" (at design time it was positioned with ${attr.name}")
                if (attr.value.isNotEmpty()) {
                    append("=${attr.value}")
                }
                append(")")
            }
            append(". Add an $adjective constraint.")
        }
        context.report(ISSUE, element, location, message)
    }

    companion object {
        private val HORIZONTAL_CONSTRAINTS = listOf(
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_LEFT_TO_LEFT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_LEFT_TO_RIGHT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_LEFT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_RIGHT_TO_RIGHT_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_START_TO_START_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_START_TO_END_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_END_TO_START_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_END_TO_END_OF
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_TOP_TO_TOP_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_TOP_TO_BOTTOM_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_TOP_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_BOTTOM_TO_BOTTOM_OF,
            SdkConstants.ATTR_LAYOUT_CONSTRAINT_BASELINE_TO_BASELINE_OF
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor can record a widget's position using designtime attributes
                such as layout_editor_absoluteX. These attributes are not applied at runtime,
                so widgets without real constraints may be laid out incorrectly. Make sure
                every widget inside a ConstraintLayout has both horizontal and vertical
                constraints.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
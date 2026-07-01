package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ConstraintLayoutDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> = listOf("*")

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
            reportMissing(context, element, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "horizontally")
        }
        if (!hasVertical) {
            reportMissing(context, element, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "vertically")
        }
    }

    private fun isHelperWidget(element: Element): Boolean {
        val name = element.nodeName
        return name.endsWith("Guideline") || name.endsWith("Barrier") || name.endsWith("Group")
    }

    private fun hasHorizontalConstraints(element: Element): Boolean {
        return HORIZONTAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }
    }

    private fun hasVerticalConstraints(element: Element): Boolean {
        return VERTICAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }
    }

    private fun reportMissing(
        context: XmlContext,
        element: Element,
        editorAttrName: String,
        direction: String
    ) {
        val attr = element.getAttributeNodeNS(TOOLS_URI, editorAttrName)
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
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val TOOLS_URI = "http://schemas.android.com/tools"

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
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
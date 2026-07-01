package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : ResourceXmlDetector() {

    companion object {
        private val CONSTRAINT_LAYOUTS = listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )

        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf"
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
        )

        private const val EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val MISSING_CONSTRAINTS = Issue.create(
            "MissingConstraints",
            "Missing Constraints in ConstraintLayout",
            """
                The layout editor allows you to place widgets anywhere on the canvas, and it records
                the current position with designtime attributes (such as `layout_editor_absoluteX`).
                These attributes are **not** applied at runtime, so if you push your layout on a
                device, the widgets may appear in a different location than shown in the editor. To
                fix this, make sure a widget has both horizontal and vertical constraints by dragging
                from the edge connections.
            """,
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
        ).setAndroidSpecific(true)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): List<String> = CONSTRAINT_LAYOUTS

    override fun visitElement(context: XmlContext, element: Element) {
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val tagName = childElement.tagName
                if (tagName.startsWith("android.support.constraint.") ||
                    tagName.startsWith("androidx.constraintlayout.")
                ) {
                    child = child.nextSibling
                    continue
                }

                val hasX = hasDesignTimePosition(childElement, EDITOR_ABSOLUTE_X)
                val hasY = hasDesignTimePosition(childElement, EDITOR_ABSOLUTE_Y)
                val hasHorizontal = hasConstraint(childElement, HORIZONTAL_CONSTRAINTS)
                val hasVertical = hasConstraint(childElement, VERTICAL_CONSTRAINTS)

                if (hasX && !hasHorizontal) {
                    context.report(
                        MISSING_CONSTRAINTS,
                        childElement,
                        context.getNameLocation(childElement),
                        "This view is not constrained horizontally: only layout_editor_absoluteX was set"
                    )
                }
                if (hasY && !hasVertical) {
                    context.report(
                        MISSING_CONSTRAINTS,
                        childElement,
                        context.getNameLocation(childElement),
                        "This view is not constrained vertically: only layout_editor_absoluteY was set"
                    )
                }
            }
            child = child.nextSibling
        }
    }

    private fun hasDesignTimePosition(element: Element, attrName: String): Boolean {
        val value = element.getAttributeNS(TOOLS_URI, attrName)
        return value.isNotBlank()
    }

    private fun hasConstraint(element: Element, attrs: List<String>): Boolean {
        for (attr in attrs) {
            val value = element.getAttributeNS(AUTO_URI, attr)
            if (value.isNotBlank()) {
                return true
            }
        }
        return false
    }
}
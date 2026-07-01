package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
        )

        private const val CLASS_CONSTRAINT_LAYOUT_3X = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CLASS_CONSTRAINT_LAYOUT_2X = "android.support.constraint.ConstraintLayout"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(CLASS_CONSTRAINT_LAYOUT_3X, CLASS_CONSTRAINT_LAYOUT_2X)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode.nodeType == Node.ELEMENT_NODE) {
                val child = childNode as Element
                if (isHelper(child.tagName)) {
                    continue
                }
                checkChild(context, child)
            }
        }
    }

    private fun isHelper(tagName: String): Boolean {
        return tagName.endsWith("Guideline") ||
                tagName.endsWith("Barrier") ||
                tagName.endsWith("Group") ||
                tagName.endsWith("Placeholder") ||
                tagName.endsWith("Flow") ||
                tagName.endsWith("Layer")
    }

    private fun checkChild(context: XmlContext, child: Element) {
        var hasHorizontal = false
        var hasVertical = false

        val attributes = child.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val namespace = attr.namespaceURI
            if (namespace == AUTO_URI) {
                val localName = attr.localName ?: continue
                if (HORIZONTAL_CONSTRAINTS.contains(localName)) {
                    hasHorizontal = true
                } else if (VERTICAL_CONSTRAINTS.contains(localName)) {
                    hasVertical = true
                }
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical -> "This view is not constrained vertically or horizontally: at runtime it will jump to the top left."
                !hasHorizontal -> "This view is not constrained horizontally: at runtime it will jump to the left."
                else -> "This view is not constrained vertically: at runtime it will jump to the top."
            }
            context.report(
                ISSUE,
                child,
                context.getNameLocation(child),
                message
            )
        }
    }
}
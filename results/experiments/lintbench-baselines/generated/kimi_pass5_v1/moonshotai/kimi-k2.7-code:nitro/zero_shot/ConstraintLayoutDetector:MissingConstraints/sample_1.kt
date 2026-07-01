package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
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
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records \
                the current position with designtime attributes (such as layout_editor_absoluteX). \
                These attributes are not applied at runtime, so if you push your layout on a device, \
                the widgets may appear in a different location than shown in the editor. To fix this, \
                make sure a widget has both horizontal and vertical constraints by dragging from the \
                edge connections.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val CONSTRAINT_LAYOUTS = listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout"
        )

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

        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"

        private val GUIDELINE_TAGS = listOf(
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return CONSTRAINT_LAYOUTS
    }

    override fun visitElement(context: XmlContext, element: Element) {
        for (child in element.children()) {
            if (child.tagName in GUIDELINE_TAGS) {
                continue
            }

            val hasHorizontal = child.hasAnyAttribute(SdkConstants.AUTO_URI, HORIZONTAL_CONSTRAINTS)
            val hasVertical = child.hasAnyAttribute(SdkConstants.AUTO_URI, VERTICAL_CONSTRAINTS)
            val hasEditorPosition =
                child.hasAttributeNS(SdkConstants.TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
                        child.hasAttributeNS(SdkConstants.TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)

            if (hasEditorPosition && (!hasHorizontal || !hasVertical)) {
                val missing = mutableListOf<String>()
                if (!hasHorizontal) missing.add("horizontally")
                if (!hasVertical) missing.add("vertically")

                val message = if (missing.size == 2) {
                    "This view is not constrained horizontally or vertically. " +
                            "Missing constraints will cause it to jump to a different position at runtime."
                } else {
                    "This view is not constrained ${missing[0]}. " +
                            "Missing constraints will cause it to jump to a different position at runtime."
                }
                context.report(ISSUE, child, context.getLocation(child), message)
            }
        }
    }

    private fun Element.hasAnyAttribute(namespaceUri: String, names: List<String>): Boolean {
        return names.any { hasAttributeNS(namespaceUri, it) }
    }

    private fun Element.children(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                result.add(node)
            }
        }
        return result
    }
}
package com.android.tools.lint.checks

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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (!isConstraintLayout(parentTag)) {
            return
        }

        val tag = element.tagName
        if (isHelper(tag)) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false
        var hasCircle = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val localName = attr.localName ?: attr.nodeName.substringAfter(':')
            if (localName.startsWith("layout_constraint")) {
                when (localName) {
                    "layout_constraintLeft_toLeftOf",
                    "layout_constraintLeft_toRightOf",
                    "layout_constraintRight_toLeftOf",
                    "layout_constraintRight_toRightOf",
                    "layout_constraintStart_toStartOf",
                    "layout_constraintStart_toEndOf",
                    "layout_constraintEnd_toStartOf",
                    "layout_constraintEnd_toEndOf" -> hasHorizontal = true

                    "layout_constraintTop_toTopOf",
                    "layout_constraintTop_toBottomOf",
                    "layout_constraintBottom_toTopOf",
                    "layout_constraintBottom_toBottomOf",
                    "layout_constraintBaseline_toBaselineOf" -> hasVertical = true

                    "layout_constraintCircle" -> hasCircle = true
                }
            }
        }

        if (hasCircle) {
            hasHorizontal = true
            hasVertical = true
        }

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical -> "This view is not constrained horizontally or vertically; at runtime it will jump to the left and top."
                !hasHorizontal -> "This view is not constrained horizontally: at runtime it will jump to the left."
                else -> "This view is not constrained vertically: at runtime it will jump to the top."
            }
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                message
            )
        }
    }

    private fun isConstraintLayout(tag: String): Boolean {
        return tag == "ConstraintLayout" ||
                tag == "android.support.constraint.ConstraintLayout" ||
                tag == "androidx.constraintlayout.widget.ConstraintLayout" ||
                tag.endsWith(".ConstraintLayout")
    }

    private fun isHelper(tag: String): Boolean {
        return tag == "Guideline" || tag.endsWith(".Guideline") ||
                tag == "Barrier" || tag.endsWith(".Barrier") ||
                tag == "Group" || tag.endsWith(".Group") ||
                tag == "Placeholder" || tag.endsWith(".Placeholder") ||
                tag == "androidx.constraintlayout.helper.widget.Flow" || tag.endsWith(".Flow") ||
                tag == "androidx.constraintlayout.widget.Guideline" ||
                tag == "androidx.constraintlayout.widget.Barrier" ||
                tag == "androidx.constraintlayout.widget.Group" ||
                tag == "androidx.constraintlayout.widget.Placeholder"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
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
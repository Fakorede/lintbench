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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("*")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        var parentTag = parent.tagName
        if (parentTag == SdkConstants.VIEW_MERGE) {
            val parentTagAttr = parent.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_PARENT_TAG)
            if (parentTagAttr == "androidx.constraintlayout.widget.ConstraintLayout" ||
                parentTagAttr == "android.support.constraint.ConstraintLayout" ||
                parentTagAttr.endsWith(".ConstraintLayout")
            ) {
                parentTag = parentTagAttr
            }
        }

        if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
            parentTag != "android.support.constraint.ConstraintLayout" &&
            !parentTag.endsWith(".ConstraintLayout")
        ) {
            return
        }

        val tagName = element.tagName
        if (tagName == "androidx.constraintlayout.widget.Guideline" ||
            tagName == "android.support.constraint.Guideline" ||
            tagName.endsWith(".Guideline") ||
            tagName == "androidx.constraintlayout.widget.Barrier" ||
            tagName == "android.support.constraint.Barrier" ||
            tagName.endsWith(".Barrier") ||
            tagName == "androidx.constraintlayout.widget.Group" ||
            tagName == "android.support.constraint.Group" ||
            tagName.endsWith(".Group") ||
            tagName == "androidx.constraintlayout.helper.widget.Layer" ||
            tagName.endsWith(".Layer") ||
            tagName == "androidx.constraintlayout.widget.Constraints" ||
            tagName.endsWith(".Constraints")
        ) {
            return
        }

        val width = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH)
        val height = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT)

        val hasCircle = element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintCircle")

        val hasHorizontal = hasCircle ||
                width == SdkConstants.VALUE_MATCH_PARENT ||
                width == SdkConstants.VALUE_FILL_PARENT ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintLeft_toLeftOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintLeft_toRightOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintRight_toLeftOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintRight_toRightOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintStart_toStartOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintStart_toEndOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintEnd_toStartOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintEnd_toEndOf")

        val hasVertical = hasCircle ||
                height == SdkConstants.VALUE_MATCH_PARENT ||
                height == SdkConstants.VALUE_FILL_PARENT ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintTop_toTopOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintTop_toBottomOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintBottom_toTopOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintBottom_toBottomOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_baselineToBaselineOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintBaseline_toBaselineOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintBaseline_toTopOf") ||
                element.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintBaseline_toBottomOf")

        if (!hasHorizontal && !hasVertical) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add the constraints"
            )
        } else if (!hasHorizontal) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
            )
        } else if (!hasVertical) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
            )
        }
    }

    companion object {
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
    }
}
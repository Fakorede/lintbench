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
            var parentTagAttr = parent.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_PARENT_TAG)
            if (parentTagAttr.isNullOrEmpty()) {
                parentTagAttr = parent.getAttribute("tools:parentTag")
            }
            if (!parentTagAttr.isNullOrEmpty()) {
                parentTag = parentTagAttr
            }
        }

        val localParentTag = parentTag.substringAfter(':')
        val isConstraintLayout = localParentTag == "ConstraintLayout" ||
                localParentTag.endsWith(".ConstraintLayout")

        if (!isConstraintLayout) {
            return
        }

        val localTagName = element.tagName.substringAfter(':')
        val simpleTagName = localTagName.substringAfterLast('.')
        if (simpleTagName == "Guideline" ||
            simpleTagName == "Barrier" ||
            simpleTagName == "Group" ||
            simpleTagName == "Layer" ||
            simpleTagName == "Constraints"
        ) {
            return
        }

        val width = getLayoutAttribute(element, SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.ANDROID_URI, "android")
        val height = getLayoutAttribute(element, SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.ANDROID_URI, "android")

        val hasCircle = hasConstraint(element, "layout_constraintCircle")

        val hasHorizontal = hasCircle ||
                width == SdkConstants.VALUE_MATCH_PARENT ||
                width == SdkConstants.VALUE_FILL_PARENT ||
                hasConstraint(element, "layout_constraintLeft_toLeftOf") ||
                hasConstraint(element, "layout_constraintLeft_toRightOf") ||
                hasConstraint(element, "layout_constraintRight_toLeftOf") ||
                hasConstraint(element, "layout_constraintRight_toRightOf") ||
                hasConstraint(element, "layout_constraintStart_toStartOf") ||
                hasConstraint(element, "layout_constraintStart_toEndOf") ||
                hasConstraint(element, "layout_constraintEnd_toStartOf") ||
                hasConstraint(element, "layout_constraintEnd_toEndOf")

        val hasVertical = hasCircle ||
                height == SdkConstants.VALUE_MATCH_PARENT ||
                height == SdkConstants.VALUE_FILL_PARENT ||
                hasConstraint(element, "layout_constraintTop_toTopOf") ||
                hasConstraint(element, "layout_constraintTop_toBottomOf") ||
                hasConstraint(element, "layout_constraintBottom_toTopOf") ||
                hasConstraint(element, "layout_constraintBottom_toBottomOf") ||
                hasConstraint(element, "layout_baselineToBaselineOf") ||
                hasConstraint(element, "layout_constraintBaseline_toBaselineOf") ||
                hasConstraint(element, "layout_constraintBaseline_toTopOf") ||
                hasConstraint(element, "layout_constraintBaseline_toBottomOf")

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

    private fun getLayoutAttribute(element: Element, name: String, ns: String, prefix: String): String {
        var value = element.getAttributeNS(ns, name)
        if (!value.isNullOrEmpty()) return value
        value = element.getAttribute("$prefix:$name")
        if (!value.isNullOrEmpty()) return value
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val nodeName = attr.nodeName
            if (nodeName == name || nodeName.endsWith(":$name")) {
                return attr.nodeValue
            }
        }
        return ""
    }

    private fun hasConstraint(element: Element, name: String): Boolean {
        if (element.hasAttributeNS(SdkConstants.AUTO_URI, name)) return true
        if (element.hasAttribute("app:$name")) return true
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val nodeName = attr.nodeName
            if (nodeName == name || nodeName.endsWith(":$name")) {
                return true
            }
        }
        return false
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
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.ALL
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

        if (!isConstraintLayout(parentTag)) {
            return
        }

        val localTagName = element.tagName.substringAfter(':')
        val simpleTagName = localTagName.substringAfterLast('.')
        if (simpleTagName == "Guideline" ||
            simpleTagName == "Barrier" ||
            simpleTagName == "Group" ||
            simpleTagName == "Placeholder" ||
            simpleTagName == "Constraints" ||
            simpleTagName == "Flow" ||
            simpleTagName == "Layer"
        ) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        var width = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH)
        if (width.isEmpty()) {
            width = element.getAttribute("android:${SdkConstants.ATTR_LAYOUT_WIDTH}")
        }
        if (width == SdkConstants.VALUE_MATCH_PARENT) {
            hasHorizontal = true
        }

        var height = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT)
        if (height.isEmpty()) {
            height = element.getAttribute("android:${SdkConstants.ATTR_LAYOUT_HEIGHT}")
        }
        if (height == SdkConstants.VALUE_MATCH_PARENT) {
            hasVertical = true
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val prefix = attr.prefix
            val localName = attr.localName ?: attr.nodeName.substringAfter(':')
            val namespace = attr.namespaceURI

            if (SdkConstants.TOOLS_URI == namespace || "tools" == prefix) {
                continue
            }

            val isApp = namespace == SdkConstants.AUTO_URI || prefix == "app" || (namespace == null && attr.nodeName.startsWith("app:"))

            if (isApp && localName.startsWith("layout_constraint")) {
                if (localName == "layout_constraintCircle") {
                    hasHorizontal = true
                    hasVertical = true
                } else if (localName.endsWith("toLeftOf") ||
                    localName.endsWith("toRightOf") ||
                    localName.endsWith("toStartOf") ||
                    localName.endsWith("toEndOf")
                ) {
                    hasHorizontal = true
                } else if (localName.endsWith("toTopOf") ||
                    localName.endsWith("toBottomOf") ||
                    localName.endsWith("BaselineOf")
                ) {
                    hasVertical = true
                }
            }
        }

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

    private fun isConstraintLayout(tag: String?): Boolean {
        if (tag == null) return false
        val local = tag.substringAfter(':')
        return local == "ConstraintLayout" ||
                local == "MotionLayout" ||
                local.endsWith(".ConstraintLayout") ||
                local.endsWith(".MotionLayout")
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
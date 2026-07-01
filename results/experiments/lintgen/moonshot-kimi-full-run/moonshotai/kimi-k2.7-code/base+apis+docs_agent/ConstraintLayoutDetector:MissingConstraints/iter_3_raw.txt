package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> = CONSTRAINT_LAYOUTS

    override fun visitElement(context: XmlContext, element: Element) {
        if (!CONSTRAINT_LAYOUTS.contains(element.tagName)) {
            return
        }

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (CONSTRAINT_LAYOUTS.contains(childElement.tagName)) {
                    // Will be analyzed when visitElement is called for the nested layout.
                } else {
                    checkElement(context, childElement)
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val tag = element.tagName
        when {
            tag == TAG_INCLUDE || tag == TAG_MERGE -> return
            tag.endsWith("Guideline") -> return
            tag.endsWith("ConstraintSet") -> return
            tag.endsWith("Barrier") || tag.endsWith("Group") -> {
                if (!element.hasAttributeNS(AUTO_URI, ATTR_CONSTRAINT_REFERENCED_IDS)) {
                    reportMissing(context, element)
                }
                return
            }
            else -> checkWidget(context, element)
        }
    }

    private fun checkWidget(context: XmlContext, element: Element) {
        val hasHorizontal = hasHorizontalConstraints(element)
        val hasVertical = hasVerticalConstraints(element)
        val widthMatchParent = isMatchParent(element, ATTR_LAYOUT_WIDTH)
        val heightMatchParent = isMatchParent(element, ATTR_LAYOUT_HEIGHT)

        if (!hasHorizontal && !widthMatchParent) {
            reportMissing(context, element, true)
        }
        if (!hasVertical && !heightMatchParent) {
            reportMissing(context, element, false)
        }
    }

    private fun hasHorizontalConstraints(element: Element): Boolean {
        return HORIZONTAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }
    }

    private fun hasVerticalConstraints(element: Element): Boolean {
        return VERTICAL_CONSTRAINTS.any { element.hasAttributeNS(AUTO_URI, it) }
    }

    private fun isMatchParent(element: Element, attributeName: String): Boolean {
        return element.getAttributeNS(ANDROID_URI, attributeName) == VALUE_MATCH_PARENT
    }

    private fun reportMissing(context: XmlContext, element: Element) {
        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "This view is not constrained. Add a constraint."
        )
    }

    private fun reportMissing(context: XmlContext, element: Element, horizontal: Boolean) {
        val editorAttrName = if (horizontal) {
            ATTR_LAYOUT_EDITOR_ABSOLUTE_X
        } else {
            ATTR_LAYOUT_EDITOR_ABSOLUTE_Y
        }
        val attribute: Attr? = element.getAttributeNodeNS(TOOLS_URI, editorAttrName)
        val adverb = if (horizontal) "horizontally" else "vertically"
        val adjective = if (horizontal) "horizontal" else "vertical"
        val message = if (attribute != null) {
            "This view is not constrained $adverb: only ${attribute.name} was set to ${attribute.value}. Add a $adjective constraint."
        } else {
            "This view is not constrained $adverb. Add a $adjective constraint."
        }
        context.report(ISSUE, element, context.getElementLocation(element), message)
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TOOLS_URI = "http://schemas.android.com/tools"

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"
        private const val ATTR_CONSTRAINT_REFERENCED_IDS = "constraint_referenced_ids"
        private const val VALUE_MATCH_PARENT = "match_parent"

        private const val TAG_INCLUDE = "include"
        private const val TAG_MERGE = "merge"

        private val CONSTRAINT_LAYOUTS = listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
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
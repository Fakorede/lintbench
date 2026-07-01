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

    override fun getApplicableElements(): Collection<String> = CONSTRAINT_LAYOUT_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element)) {
            return
        }

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        if (isHelperWidget(child)) {
            return
        }

        val hasHorizontal = hasHorizontalConstraints(child)
        val hasVertical = hasVerticalConstraints(child)
        val widthMatchParent = isMatchParent(child, ATTR_LAYOUT_WIDTH)
        val heightMatchParent = isMatchParent(child, ATTR_LAYOUT_HEIGHT)

        if (!hasHorizontal && !widthMatchParent) {
            reportMissing(context, child, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, true)
        }
        if (!hasVertical && !heightMatchParent) {
            reportMissing(context, child, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, false)
        }
    }

    private fun isConstraintLayout(element: Element): Boolean {
        return CONSTRAINT_LAYOUT_TAGS.contains(element.nodeName)
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

    private fun isMatchParent(element: Element, attribute: String): Boolean {
        return element.getAttributeNS(ANDROID_URI, attribute) == VALUE_MATCH_PARENT
    }

    private fun reportMissing(
        context: XmlContext,
        element: Element,
        editorAttrName: String,
        horizontal: Boolean
    ) {
        val adverb = if (horizontal) "horizontally" else "vertically"
        val adjective = if (horizontal) "horizontal" else "vertical"
        val attr: Attr? = element.getAttributeNodeNS(TOOLS_URI, editorAttrName)
        val message = if (attr != null) {
            "This view is not constrained $adverb: only ${attr.name} was set to ${attr.value}. Add an $adjective constraint."
        } else {
            "This view is not constrained $adverb. Add an $adjective constraint."
        }
        val location = attr?.let { context.getLocation(it) } ?: context.getElementLocation(element)
        context.report(ISSUE, element, location, message)
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TOOLS_URI = "http://schemas.android.com/tools"

        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
        private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"
        private const val VALUE_MATCH_PARENT = "match_parent"

        private val CONSTRAINT_LAYOUT_TAGS = listOf(
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
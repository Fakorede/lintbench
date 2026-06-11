package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ConstraintLayoutDetector : Detector(), XmlScanner {

    companion object Issues {
        val MISSING_CONSTRAINTS = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf("androidx.constraintlayout.widget.ConstraintLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return

        if (parent.tagName == "androidx.constraintlayout.widget.ConstraintLayout") {
            checkConstraints(context, element)
        }
    }

    private fun checkConstraints(context: XmlContext, element: Element) {
        val horizontalConstraints = getConstraintAttributes(element, listOf("layout_constraintLeft_toLeftOf", "layout_constraintRight_toRightOf"))
        val verticalConstraints = getConstraintAttributes(element, listOf("layout_constraintTop_toTopOf", "layout_constraintBottom_toBottomOf"))

        if (horizontalConstraints.isEmpty() && verticalConstraints.isEmpty()) {
            context.report(
                MISSING_CONSTRAINTS,
                element,
                context.getLocation(element),
                "Widget has no horizontal and vertical constraints"
            )
        } else if (horizontalConstraints.isEmpty()) {
            context.report(
                MISSING_CONSTRAINTS,
                element,
                context.getLocation(element),
                "Widget is missing horizontal constraints"
            )
        } else if (verticalConstraints.isEmpty()) {
            context.report(
                MISSING_CONSTRAINTS,
                element,
                context.getLocation(element),
                "Widget is missing vertical constraints"
            )
        }
    }

    private fun getConstraintAttributes(element: Element, attributeNames: List<String>): List<Attr> {
        return attributeNames.mapNotNull { attrName ->
            val attr = element.getAttributeNode(attrName)
            if (attr != null && !attr.value.isNullOrEmpty()) attr else null
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitDocument(context: XmlContext, document: Document) {}
}
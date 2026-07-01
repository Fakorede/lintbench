package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ConstraintLayoutDetector : Detector(), XmlScanner {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (!isConstraintLayout(parentTag)) {
            return
        }

        val tag = element.tagName
        if (isSkipElement(tag)) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val width = element.getAttributeNS(ANDROID_URI, "layout_width")
        if (width == "match_parent" || width == "fill_parent") {
            hasHorizontal = true
        }

        val height = element.getAttributeNS(ANDROID_URI, "layout_height")
        if (height == "match_parent" || height == "fill_parent") {
            hasVertical = true
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val namespace = attr.namespaceURI
            if (namespace == AUTO_URI) {
                val name = attr.localName ?: attr.name.substringAfter(':')
                when (name) {
                    "layout_constraintLeft_toLeftOf",
                    "layout_constraintLeft_toRightOf",
                    "layout_constraintRight_toLeftOf",
                    "layout_constraintRight_toRightOf",
                    "layout_constraintStart_toEndOf",
                    "layout_constraintStart_toStartOf",
                    "layout_constraintEnd_toStartOf",
                    "layout_constraintEnd_toEndOf" -> hasHorizontal = true

                    "layout_constraintTop_toTopOf",
                    "layout_constraintTop_toBottomOf",
                    "layout_constraintBottom_toTopOf",
                    "layout_constraintBottom_toBottomOf",
                    "layout_constraintBaseline_toBaselineOf" -> hasVertical = true

                    "layout_constraintCircle" -> {
                        hasHorizontal = true
                        hasVertical = true
                    }
                }
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical -> "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add the constraints"
                !hasHorizontal -> "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
                else -> "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
            }
            context.report(ISSUE, element, context.getNameLocation(element), message)
        }
    }

    private fun isConstraintLayout(tag: String): Boolean {
        return tag == "androidx.constraintlayout.widget.ConstraintLayout" ||
                tag == "android.support.constraint.ConstraintLayout" ||
                tag.endsWith(".ConstraintLayout") ||
                tag == "ConstraintLayout" ||
                tag == "androidx.constraintlayout.motion.widget.MotionLayout" ||
                tag.endsWith(".MotionLayout") ||
                tag == "MotionLayout"
    }

    private fun isSkipElement(tag: String): Boolean {
        return tag == "Guideline" || tag.endsWith(".Guideline") ||
                tag == "Barrier" || tag.endsWith(".Barrier") ||
                tag == "Group" || tag.endsWith(".Group") ||
                tag == "androidx.constraintlayout.widget.Constraints" ||
                tag == "androidx.constraintlayout.widget.Placeholder" ||
                tag == "Placeholder" ||
                tag == "include" ||
                tag == "merge"
    }
}
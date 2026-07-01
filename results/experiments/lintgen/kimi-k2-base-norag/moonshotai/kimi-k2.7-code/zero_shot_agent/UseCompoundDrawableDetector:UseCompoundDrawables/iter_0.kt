package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.SdkConstants.ATTR_ORIENTATION
import com.android.tools.lint.detector.api.SdkConstants.ATTR_SRC
import com.android.tools.lint.detector.api.SdkConstants.IMAGE_VIEW
import com.android.tools.lint.detector.api.SdkConstants.LINEAR_LAYOUT
import com.android.tools.lint.detector.api.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.SdkConstants.VALUE_VERTICAL
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String> = setOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != LINEAR_LAYOUT) {
            return
        }

        val children = element.childElements()
        if (children.size != 2) {
            return
        }

        val imageView = children.find { it.tagName == IMAGE_VIEW }
        val textView = children.find { it.tagName == TEXT_VIEW }
        if (imageView == null || textView == null) {
            return
        }

        // A compound drawable is set from the ImageView's src attribute.
        if (imageView.getAttributeNS(ANDROID_URI, ATTR_SRC).isBlank()) {
            return
        }

        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
        val isVertical = orientation == VALUE_VERTICAL

        val imageIndex = children.indexOf(imageView)
        val textIndex = children.indexOf(textView)
        val drawableAttr = when {
            isVertical && imageIndex < textIndex -> "android:drawableTop"
            isVertical -> "android:drawableBottom"
            imageIndex < textIndex -> "android:drawableLeft"
            else -> "android:drawableRight"
        }

        val message = "This LinearLayout and its children can be replaced by a single " +
                "<TextView> using `$drawableAttr` (use `android:drawablePadding` for any margins)"

        context.report(ISSUE, element, context.getLocation(element), message)
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }

    companion object {
        private val EXPLANATION = """
            A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
            efficiently handled as a compound drawable (a single `TextView`, using the
            `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
            attributes to draw one or more images adjacent to the text).

            If the two widgets are offset from each other with margins, this can be
            replaced with a `drawablePadding` attribute.
        """.trimIndent()

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = EXPLANATION,
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
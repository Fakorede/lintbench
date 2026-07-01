package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("LinearLayout")

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        var node: Node? = element.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                children.add(node as Element)
            }
            node = node.nextSibling
        }

        if (children.size != 2) return

        val orientation = element.getAttribute("android:orientation")
        val isVertical = orientation == "vertical"

        val imageView = children.find { it.tagName == "ImageView" }
        val textView = children.find { it.tagName == "TextView" }

        if (imageView == null || textView == null) return

        // Compound drawables do not support layout weights
        if (imageView.hasAttribute("android:layout_weight") ||
            textView.hasAttribute("android:layout_weight")) {
            return
        }

        val imageIndex = children.indexOf(imageView)
        val textIndex = children.indexOf(textView)

        val drawableAttr = when {
            isVertical && imageIndex < textIndex -> "drawableTop"
            isVertical && imageIndex > textIndex -> "drawableBottom"
            !isVertical && imageIndex < textIndex -> "drawableLeft"
            !isVertical && imageIndex > textIndex -> "drawableRight"
            else -> return
        }

        val hasMargins = imageView.hasAttribute("android:layout_margin") ||
            imageView.hasAttribute("android:layout_marginStart") ||
            imageView.hasAttribute("android:layout_marginEnd") ||
            imageView.hasAttribute("android:layout_marginTop") ||
            imageView.hasAttribute("android:layout_marginBottom") ||
            textView.hasAttribute("android:layout_margin") ||
            textView.hasAttribute("android:layout_marginStart") ||
            textView.hasAttribute("android:layout_marginEnd") ||
            textView.hasAttribute("android:layout_marginTop") ||
            textView.hasAttribute("android:layout_marginBottom")

        val message = buildString {
            append("This tag and its children can be replaced by one <TextView/> and a compound $drawableAttr")
            if (hasMargins) {
                append(" (margins can be converted to drawablePadding)")
            }
        }

        context.report(
            ISSUE,
            context.getLocation(element),
            message
        )
    }
}
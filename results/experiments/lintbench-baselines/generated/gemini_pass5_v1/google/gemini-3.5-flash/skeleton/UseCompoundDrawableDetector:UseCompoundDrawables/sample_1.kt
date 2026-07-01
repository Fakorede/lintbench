package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            UseCompoundDrawableDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more 
                efficiently handled as a compound drawable (a single TextView, using the 
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` 
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be 
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = getChildren(element)
        if (children.size != 2) {
            return
        }

        var imageView: Element? = null
        var textView: Element? = null

        for (child in children) {
            val tagName = child.tagName
            if (tagName == "ImageView") {
                imageView = child
            } else if (tagName == "TextView") {
                textView = child
            }
        }

        if (imageView == null || textView == null) {
            return
        }

        // If either view has a layout_weight, don't suggest compound drawables
        if (imageView.hasAttributeNS(ANDROID_URI, "layout_weight") ||
            textView.hasAttributeNS(ANDROID_URI, "layout_weight")) {
            return
        }

        // If the ImageView has a click listener, it should remain separate
        if (imageView.hasAttributeNS(ANDROID_URI, "onClick")) {
            return
        }

        // If the ImageView has a background, it might be custom styled and not easily convertible
        if (imageView.hasAttributeNS(ANDROID_URI, "background")) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a `<TextView>` with compound drawables"
        )
    }

    private fun getChildren(element: Element): List<Element> {
        val list = mutableListOf<Element>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                list.add(child as Element)
            }
        }
        return list
    }
}
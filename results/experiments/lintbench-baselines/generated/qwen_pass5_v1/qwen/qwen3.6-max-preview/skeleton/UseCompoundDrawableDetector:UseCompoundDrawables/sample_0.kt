package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more efficiently handled as a compound drawable (a single TextView, using the `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more images adjacent to the text).\n\nIf the two widgets are offset from each other with margins, this can be replaced with a `drawablePadding` attribute.\n\nThere's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val LINEAR_LAYOUT = "LinearLayout"
        private const val IMAGE_VIEW = "ImageView"
        private const val TEXT_VIEW = "TextView"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.hasAttributeNS(ANDROID_URI, "weightSum")) return

        var imageElement: Element? = null
        var textElement: Element? = null
        var childElementCount = 0

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                childElementCount++
                val tag = (child as Element).tagName
                if (tag == IMAGE_VIEW || tag.endsWith(".$IMAGE_VIEW")) {
                    if (imageElement != null) return
                    imageElement = child
                } else if (tag == TEXT_VIEW || tag.endsWith(".$TEXT_VIEW")) {
                    if (textElement != null) return
                    textElement = child
                } else {
                    return
                }

                if (child.hasAttributeNS(ANDROID_URI, "layout_weight")) return
            }
            child = child.nextSibling
        }

        if (childElementCount == 2 && imageElement != null && textElement != null) {
            context.report(
                ISSUE,
                context.getNameLocation(element),
                "This tag and its children can be replaced by one <TextView/> and a compound drawable"
            )
        }
    }
}
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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : Detector(), Detector.XmlScanner {
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

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        var imageView: Element? = null
        var textView: Element? = null
        var childCount = 0

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                childCount++
                val childElement = child as Element
                when (childElement.tagName) {
                    "ImageView" -> imageView = childElement
                    "TextView" -> textView = childElement
                }
            }
        }

        if (childCount == 2 && imageView != null && textView != null) {
            val androidUri = "http://schemas.android.com/apk/res/android"
            val imageHasWeight = imageView.hasAttributeNS(androidUri, "layout_weight")
            val textHasWeight = textView.hasAttributeNS(androidUri, "layout_weight")
            if (imageHasWeight || textHasWeight) {
                return
            }

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag and its children can be replaced by one <TextView/> and a compound drawable"
            )
        }
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var childCount = 0
        var imageView: Element? = null
        var textView: Element? = null

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                childCount++
                if (childCount > 2) {
                    return
                }
                val tag = child.nodeName
                if (tag == IMAGE_VIEW) {
                    imageView = child as Element
                } else if (tag == TEXT_VIEW) {
                    textView = child as Element
                }
            }
        }

        if (childCount != 2 || imageView == null || textView == null) {
            return
        }

        // If the ImageView has a click listener, it's not a compound drawable candidate
        if (imageView.hasAndroidAttribute("clickable") ||
            imageView.hasAndroidAttribute("onClick") ||
            imageView.hasAndroidAttribute("focusable")
        ) {
            return
        }

        // If the ImageView has a weight, it's not a compound drawable candidate
        if (imageView.hasAndroidAttribute("layout_weight")) {
            return
        }

        // If the ImageView doesn't have a src, it's not a compound drawable candidate
        if (!imageView.hasAndroidAttribute("src") &&
            !imageView.hasAppAttribute("srcCompat")
        ) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
        )
    }

    private fun Element.hasAndroidAttribute(name: String): Boolean {
        return hasAttributeNS(ANDROID_URI, name) || hasAttribute("android:$name")
    }

    private fun Element.hasAppAttribute(name: String): Boolean {
        return hasAttributeNS(AUTO_URI, name) || hasAttribute("app:$name")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A LinearLayout which contains an ImageView and a TextView can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                drawableTop, drawableLeft, drawableRight and/or drawableBottom \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a drawablePadding attribute.

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
}
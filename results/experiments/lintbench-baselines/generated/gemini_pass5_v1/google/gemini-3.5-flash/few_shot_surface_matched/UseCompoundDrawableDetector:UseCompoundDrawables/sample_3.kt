package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class UseCompoundDrawableDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val childNodes = element.childNodes
        val children = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is org.w3c.dom.Element) {
                children.add(child)
            }
        }

        if (children.size != 2) {
            return
        }

        var imageView: org.w3c.dom.Element? = null
        var textView: org.w3c.dom.Element? = null

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

        val androidUri = "http://schemas.android.com/apk/res/android"
        if (imageView.hasAttributeNS(androidUri, "clickable") ||
            imageView.hasAttributeNS(androidUri, "focusable") ||
            imageView.hasAttributeNS(androidUri, "id")) {
            return
        }

        if (imageView.hasAttributeNS(androidUri, "layout_weight") ||
            textView.hasAttributeNS(androidUri, "layout_weight")) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a `<TextView/>` with compound drawables"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Can be replaced with a TextView with compound drawables",
            explanation = "A LinearLayout which contains an ImageView and a TextView can be more " +
                    "efficiently handled as a compound drawable (a single TextView, using the " +
                    "drawableTop, drawableLeft, drawableRight and/or drawableBottom " +
                    "attributes to draw one or more images adjacent to the text). " +
                    "If the two widgets are offset from each other with margins, this can be " +
                    "replaced with a drawablePadding attribute.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILE_SCOPE
            )
        )
    }
}
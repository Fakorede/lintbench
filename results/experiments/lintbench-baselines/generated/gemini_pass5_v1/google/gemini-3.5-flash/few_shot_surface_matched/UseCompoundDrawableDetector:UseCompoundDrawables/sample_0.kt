package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childElements = mutableListOf<Element>()
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                childElements.add(child as Element)
            }
            child = child.nextSibling
        }

        if (childElements.size != 2) {
            return
        }

        var imageView: Element? = null
        var textView: Element? = null

        for (c in childElements) {
            val tagName = c.tagName
            if (tagName == "ImageView") {
                imageView = c
            } else if (tagName == "TextView") {
                textView = c
            }
        }

        if (imageView == null || textView == null) {
            return
        }

        val androidUri = "http://schemas.android.com/apk/res/android"

        if (imageView.hasAttributeNS(androidUri, "layout_weight") ||
            textView.hasAttributeNS(androidUri, "layout_weight")) {
            return
        }

        if (imageView.getAttributeNS(androidUri, "clickable") == "true" ||
            imageView.getAttributeNS(androidUri, "focusable") == "true") {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a `<TextView/>` using compound drawables"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Can be replaced with a compound drawable",
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                "efficiently handled as a compound drawable (a single `TextView`, using the " +
                "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                "attributes to draw one or more images adjacent to the text).\n\n" +
                "If the two widgets are offset from each other with margins, this can be " +
                "replaced with a `drawablePadding` attribute.\n\n" +
                "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}
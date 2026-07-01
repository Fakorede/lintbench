package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "LinearLayout") return

        val children = mutableListOf<org.w3c.dom.Element>()
        var child = element.firstChild
        while (child != null) {
            if (child is org.w3c.dom.Element) {
                children.add(child)
            }
            child = child.nextSibling
        }

        if (children.size != 2) return

        val first = children[0]
        val second = children[1]
        val firstTag = first.tagName
        val secondTag = second.tagName

        val imageView = if (firstTag == "ImageView") first else if (secondTag == "ImageView") second else null
        val textView = if (firstTag == "TextView") first else if (secondTag == "TextView") second else null

        if (imageView == null || textView == null) return

        val androidUri = "http://schemas.android.com/apk/res/android"

        if (imageView.hasAttributeNS(androidUri, "background")) return

        if (imageView.hasAttributeNS(androidUri, "clickable") && 
            imageView.getAttributeNS(androidUri, "clickable") == "true") return

        if (imageView.hasAttributeNS(androidUri, "onClick")) return

        if (imageView.hasAttributeNS(androidUri, "layout_weight") || 
            textView.hasAttributeNS(androidUri, "layout_weight")) return

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "TextView can be combined with ImageView",
            explanation = "A LinearLayout which contains an ImageView and a TextView can be more " +
                "efficiently handled as a compound drawable (a single TextView, using the " +
                "drawableTop, drawableLeft, drawableRight and/or drawableBottom attributes to draw " +
                "one or more images adjacent to the text).\n\n" +
                "If the two widgets are offset from each other with margins, this can be " +
                "replaced with a drawablePadding attribute.",
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
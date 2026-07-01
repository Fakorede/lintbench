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

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("LinearLayout")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val children = element.childNodes

        var imageView: org.w3c.dom.Element? = null
        var textView: org.w3c.dom.Element? = null
        var childCount = 0

        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            childCount++
            if (childCount > 2) {
                return
            }
            val tag = child.localName ?: child.tagName.substringAfterLast(':')
            when (tag) {
                "ImageView" -> imageView = child
                "TextView" -> textView = child
                else -> return
            }
        }

        if (imageView == null || textView == null) {
            return
        }

        if (getAttribute(imageView, "src").isNullOrBlank() ||
            getAttribute(textView, "text").isNullOrBlank()
        ) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "This layout and its children can be replaced by a single `TextView` with compound drawables"
        )
    }

    private fun getAttribute(element: org.w3c.dom.Element, name: String): String? {
        val nsValue = element.getAttributeNS(ANDROID_URI, name)
        if (nsValue.isNotBlank()) {
            return nsValue
        }
        val localValue = element.getAttribute(name)
        return if (localValue.isNotBlank()) localValue else null
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                "efficiently handled as a compound drawable (a single `TextView`, using the `drawableTop`, " +
                "`drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more " +
                "images adjacent to the text). If the two widgets are offset from each other with margins, " +
                "this can be replaced with a `drawablePadding` attribute.",
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
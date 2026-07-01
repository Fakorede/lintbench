package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class UseCompoundDrawableDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        var imageView: Element? = null
        var textView: Element? = null

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val tag = (child as Element).tagName
            when (tag) {
                TAG_IMAGE_VIEW -> imageView = child
                TAG_TEXT_VIEW -> textView = child
                else -> return
            }
        }

        if (imageView == null || textView == null) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This LinearLayout can be replaced by a TextView with compound drawables"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = "A LinearLayout which contains an ImageView and a TextView can be more " +
                    "efficiently handled as a compound drawable (a single TextView, using the " +
                    "drawableTop, drawableLeft, drawableRight and/or drawableBottom attributes " +
                    "to draw one or more images adjacent to the text). If the two widgets are " +
                    "offset from each other with margins, this can be replaced with a " +
                    "drawablePadding attribute.",
            category = Category.PERFORMANCE,
            priority = 2,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        const val TAG_LINEAR_LAYOUT = "LinearLayout"
        const val TAG_IMAGE_VIEW = "ImageView"
        const val TAG_TEXT_VIEW = "TextView"
    }
}
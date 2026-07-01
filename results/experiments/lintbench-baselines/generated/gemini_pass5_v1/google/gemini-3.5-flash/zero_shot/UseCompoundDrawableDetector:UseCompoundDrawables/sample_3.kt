package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_ON_CLICK
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.resources.ResourceFolderType
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(LINEAR_LAYOUT)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var count = 0
        var imageView: Element? = null
        var textView: Element? = null

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val tagName = childElement.tagName
                if (tagName == IMAGE_VIEW) {
                    if (imageView != null) {
                        return
                    }
                    imageView = childElement
                } else if (tagName == TEXT_VIEW) {
                    if (textView != null) {
                        return
                    }
                    textView = childElement
                } else {
                    return
                }
                count++
            }
            child = child.nextSibling
        }

        if (count == 2 && imageView != null && textView != null) {
            if (imageView.hasAttributeNS(ANDROID_URI, ATTR_ID) ||
                imageView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND) ||
                imageView.hasAttributeNS(ANDROID_URI, ATTR_CLICKABLE) ||
                imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT) ||
                imageView.hasAttributeNS(ANDROID_URI, ATTR_ON_CLICK) ||
                imageView.hasAttributeNS(ANDROID_URI, ATTR_FOREGROUND)
            ) {
                return
            }

            if (imageView.hasAttribute("style")) {
                return
            }

            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A LinearLayout which contains an ImageView and a TextView can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                drawableTop, drawableLeft, drawableRight and/or drawableBottom attributes \
                to draw one or more images adjacent to the text).

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
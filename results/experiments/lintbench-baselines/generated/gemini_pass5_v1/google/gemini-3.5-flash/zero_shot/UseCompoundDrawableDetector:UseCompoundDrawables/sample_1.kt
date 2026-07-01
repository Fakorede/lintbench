package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_FOCUSABLE
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val children = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
        }

        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val imageView: Element
        val textView: Element

        if (first.tagName == IMAGE_VIEW && second.tagName == TEXT_VIEW) {
            imageView = first
            textView = second
        } else if (first.tagName == TEXT_VIEW && second.tagName == IMAGE_VIEW) {
            textView = first
            imageView = second
        } else {
            return
        }

        // If the ImageView has an ID, it might be referenced in code (e.g., to set the image dynamically or set click listeners)
        if (imageView.hasAttributeNS(ANDROID_URI, "id")) {
            return
        }

        // If the ImageView is clickable or focusable, we cannot safely merge it into the TextView
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_CLICKABLE) &&
            imageView.getAttributeNS(ANDROID_URI, ATTR_CLICKABLE) == "true"
        ) {
            return
        }
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_FOCUSABLE) &&
            imageView.getAttributeNS(ANDROID_URI, ATTR_FOCUSABLE) == "true"
        ) {
            return
        }
        if (imageView.hasAttributeNS(ANDROID_URI, "onClick")) {
            return
        }

        // Backgrounds, foregrounds, and visibilities on the ImageView prevent simple conversion
        if (imageView.hasAttributeNS(ANDROID_URI, "background") ||
            imageView.hasAttributeNS(ANDROID_URI, "foreground") ||
            imageView.hasAttributeNS(ANDROID_URI, "visibility")
        ) {
            return
        }

        // Style on the ImageView might contain properties that cannot be easily merged
        if (imageView.hasAttribute("style")) {
            return
        }

        // If either view has weight, the layout behavior is complex and shouldn't be blindly converted
        if (imageView.hasAttributeNS(ANDROID_URI, "layout_weight") ||
            textView.hasAttributeNS(ANDROID_URI, "layout_weight")
        ) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a `<TextView>` equipped with compound drawables"
        )
    }

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
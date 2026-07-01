package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.TAG_IMAGE_VIEW
import com.android.SdkConstants.TAG_LINEAR_LAYOUT
import com.android.SdkConstants.TAG_TEXT_VIEW
import com.android.SdkConstants.VALUE_HORIZONTAL
import com.android.SdkConstants.VALUE_WRAP_CONTENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = mutableListOf<Element>()
        var child = XmlUtils.getFirstChildElement(element)
        while (child != null) {
            children.add(child)
            child = XmlUtils.getNextElement(child)
        }

        if (children.size != 2) return

        val view1 = children[0]
        val view2 = children[1]

        val isImage1 = view1.tagName == TAG_IMAGE_VIEW
        val isText1 = view1.tagName == TAG_TEXT_VIEW
        val isImage2 = view2.tagName == TAG_IMAGE_VIEW
        val isText2 = view2.tagName == TAG_TEXT_VIEW

        if (!(isImage1 && isText2 || isText1 && isImage2)) return

        val orientationAttr = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
        val isHorizontal = orientationAttr.isEmpty() || orientationAttr == VALUE_HORIZONTAL

        val width1 = view1.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val height1 = view1.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
        val width2 = view2.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        val height2 = view2.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)

        if (width1 != VALUE_WRAP_CONTENT || height1 != VALUE_WRAP_CONTENT ||
            width2 != VALUE_WRAP_CONTENT || height2 != VALUE_WRAP_CONTENT) {
            return
        }

        if (view1.hasAttributeNS(ANDROID_URI, "layout_weight") ||
            view2.hasAttributeNS(ANDROID_URI, "layout_weight")) {
            return
        }

        val direction = if (isHorizontal) "left/right" else "top/bottom"
        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = "A LinearLayout which contains an ImageView and a TextView can be more " +
                "efficiently handled as a compound drawable (a single TextView, using the " +
                "drawableTop, drawableLeft, drawableRight and/or drawableBottom attributes to " +
                "draw one or more images adjacent to the text).\n\n" +
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
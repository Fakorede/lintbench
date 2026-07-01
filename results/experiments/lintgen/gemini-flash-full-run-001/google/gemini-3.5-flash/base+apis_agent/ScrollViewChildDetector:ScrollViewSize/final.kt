package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
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

class ScrollViewChildDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            SdkConstants.SCROLL_VIEW,
            SdkConstants.HORIZONTAL_SCROLL_VIEW,
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW
        val targetAttribute = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
        val dimensionName = if (isHorizontal) "width" else "height"

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val attrValue = childElement.getAttributeNS(ANDROID_URI, targetAttribute)
                if (attrValue == VALUE_MATCH_PARENT || attrValue == VALUE_FILL_PARENT) {
                    val msg = "ScrollView children should set their layout_$dimensionName to wrap_content rather than $attrValue"
                    val attributeNode = childElement.getAttributeNodeNS(ANDROID_URI, targetAttribute)
                    val location = if (attributeNode != null) {
                        context.getLocation(attributeNode)
                    } else {
                        context.getLocation(childElement)
                    }
                    context.report(ISSUE, childElement, location, msg)
                }
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes " +
                    "to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
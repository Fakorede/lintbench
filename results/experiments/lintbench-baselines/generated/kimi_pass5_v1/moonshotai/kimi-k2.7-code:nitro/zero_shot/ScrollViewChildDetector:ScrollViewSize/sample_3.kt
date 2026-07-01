package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlUtils
import org.w3c.dom.Element

class ScrollViewChildDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        SdkConstants.SCROLL_VIEW,
        SdkConstants.HORIZONTAL_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val child = XmlUtils.getFirstSubTagByTraversingOnlyChildElements(element) ?: return

        val attrName = if (element.tagName == SdkConstants.HORIZONTAL_SCROLL_VIEW) {
            SdkConstants.ATTR_LAYOUT_WIDTH
        } else {
            SdkConstants.ATTR_LAYOUT_HEIGHT
        }

        val attr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, attrName) ?: return
        val value = attr.value

        if (value == SdkConstants.VALUE_FILL_PARENT || value == SdkConstants.VALUE_MATCH_PARENT) {
            context.report(
                ISSUE,
                context.getValueLocation(attr),
                "The $attrName of a ScrollView child should be set to wrap_content, not $value"
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView children should not fill the parent in the scrollable dimension",
            explanation = """
                ScrollView children must set their layout_width (for HorizontalScrollView) or
                layout_height (for ScrollView) attributes to wrap_content rather than
                fill_parent or match_parent in the scrolling dimension.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
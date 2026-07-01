package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val child = element.firstElementChild() ?: return

    val dimension = if (element.tagName == SCROLL_VIEW) ATTR_LAYOUT_HEIGHT else ATTR_LAYOUT_WIDTH

    val attr = child.getAttributeNodeNS(ANDROID_URI, dimension) ?: return
    val value = attr.value
    if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
      context.report(
        ISSUE,
        attr,
        context.getValueLocation(attr),
        "This ${child.tagName} should use `android:${dimension}=\"wrap_content\"`"
      )
    }
  }

  private fun Element.firstElementChild(): Element? {
    var node = this.firstChild
    while (node != null) {
      if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        return node as Element
      }
      node = node.nextSibling
    }
    return null
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewSize",
      briefDescription = "ScrollView children must use wrap_content",
      explanation = "A ScrollView/HorizontalScrollView can only scroll its child when the " +
        "child's size in the scrolling dimension is larger than the ScrollView. Setting " +
        "`layout_height` (for ScrollView) or `layout_width` (for HorizontalScrollView) to " +
        "`match_parent` or `fill_parent` makes the child the same size as the ScrollView, so " +
        "scrolling will not work. Use `wrap_content` instead.",
      category = Category.CORRECTNESS,
      priority = 4,
      severity = Severity.WARNING,
      implementation = Implementation(
        ScrollViewChildDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}
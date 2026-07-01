package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      com.android.SdkConstants.SCROLL_VIEW,
      com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val horizontal = element.tagName == com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
    val dimension = if (horizontal) "layout_width" else "layout_height"
    val attribute = if (horizontal) {
      com.android.SdkConstants.ATTR_LAYOUT_WIDTH
    } else {
      com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
    }

    val child = getFirstChildElement(element) ?: return
    val layoutDimension = child.getAttributeNodeNS(
      com.android.SdkConstants.ANDROID_URI,
      attribute
    ) ?: return

    val value = layoutDimension.value
    if (value == com.android.SdkConstants.VALUE_MATCH_PARENT ||
        value == com.android.SdkConstants.VALUE_FILL_PARENT) {
      context.report(
        ISSUE,
        layoutDimension,
        context.getValueLocation(layoutDimension),
        "ScrollView children should set their $dimension to wrap_content, not $value"
      )
    }
  }

  private fun getFirstChildElement(element: org.w3c.dom.Element): org.w3c.dom.Element? {
    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        return child as? org.w3c.dom.Element
      }
      child = child.nextSibling
    }
    return null
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewSize",
      briefDescription = "ScrollView size validation",
      explanation = "ScrollView children must set their `layout_width` or `layout_height` " +
        "attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the " +
        "scrolling dimension. When a ScrollView's child is set to `match_parent` or " +
        "`fill_parent` in the scrolling direction, the child will be sized to fit the " +
        "ScrollView and will not actually scroll.",
      category = Category.CORRECTNESS,
      priority = 4,
      severity = Severity.WARNING,
      implementation = Implementation(ScrollViewChildDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
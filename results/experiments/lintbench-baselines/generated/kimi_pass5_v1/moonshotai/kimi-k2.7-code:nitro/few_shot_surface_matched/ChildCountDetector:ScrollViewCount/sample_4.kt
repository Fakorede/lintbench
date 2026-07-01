package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

  override fun getApplicableElements(): Collection<String> {
    return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    var childCount = 0
    val children = element.childNodes
    for (i in 0 until children.length) {
      if (children.item(i) is Element) {
        childCount++
        if (childCount > 1) {
          break
        }
      }
    }

    if (childCount > 1) {
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "A `${element.tagName}` can only have one child widget. " +
          "If you want more children, wrap them in a container layout."
      )
    }
  }

  companion object {
    private const val SCROLL_VIEW = "ScrollView"
    private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewCount",
      briefDescription = "ScrollView has too many children",
      explanation = "A ScrollView can only have one child widget. " +
        "If you want more children, wrap them in a container layout.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
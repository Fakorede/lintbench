package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ChildCountDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("ScrollView")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    var childCount = 0
    for (i in 0 until children.length) {
      val child = children.item(i) ?: continue
      if (child is org.w3c.dom.Element) {
        childCount++
        if (childCount > 1) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "A ScrollView can only have one child widget. If you want more children, wrap them in a container layout."
          )
          return
        }
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewCount",
      briefDescription = "ScrollView has too many children",
      explanation = "A ScrollView can only have one child widget. If you want more children, wrap them in a container layout.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
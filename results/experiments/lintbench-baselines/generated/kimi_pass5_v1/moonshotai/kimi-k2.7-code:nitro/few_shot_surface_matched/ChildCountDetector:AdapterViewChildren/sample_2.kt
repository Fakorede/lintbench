package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ChildCountDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): List<String> = ADAPTER_VIEW_TAGS

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      if (children.item(i).nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        context.report(
          ISSUE,
          element,
          context.getElementLocation(element),
          "`AdapterView` subclasses such as `ListView` cannot have children in XML; configure them with an `Adapter` from code"
        )
        return
      }
    }
  }

  companion object {
    private val ADAPTER_VIEW_TAGS = listOf(
      "AdapterView",
      "ListView",
      "GridView",
      "Spinner",
      "ExpandableListView",
      "Gallery",
      "StackView",
      "AdapterViewAnimator",
      "AdapterViewFlipper"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "AdapterViewChildren",
      briefDescription = "AdapterView cannot have children",
      explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, " +
        "such as a `ListAdapter`. AdapterView subclasses cannot have child views declared in XML.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
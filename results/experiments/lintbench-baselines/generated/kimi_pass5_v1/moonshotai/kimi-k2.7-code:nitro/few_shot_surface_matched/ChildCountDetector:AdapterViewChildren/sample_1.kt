package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ChildCountDetector : LayoutDetector() {

  override fun getApplicableElements(): Collection<String> = APPLICABLE_VIEWS

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    var child = element.firstChild
    while (child != null) {
      if (child is org.w3c.dom.Element) {
        context.report(
          ISSUE,
          child,
          context.getLocation(child),
          "AdapterView layouts cannot have children in XML"
        )
      }
      child = child.nextSibling
    }
  }

  companion object {
    private val APPLICABLE_VIEWS = listOf(
      "AdapterView",
      "ListView",
      "GridView",
      "Spinner",
      "Gallery",
      "StackView"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "AdapterViewChildren",
      briefDescription = "AdapterView cannot have children",
      explanation = "An AdapterView such as a ListView must be configured with data from Java " +
        "code, such as a ListAdapter. Child views declared in XML are ignored at runtime and are " +
        "not supported.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
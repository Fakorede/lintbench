package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  override fun beforeCheckRootProject(context: Context) {
  }

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("style")
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return emptyList()
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val item = child as org.w3c.dom.Element
        if (item.tagName == "item" && item.getAttribute("name") == "android:id") {
          val value = item.textContent.trim()
          if (value.startsWith("@+id/")) {
            context.report(
              ISSUE,
              item,
              context.getLocation(item),
              "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
            )
          }
        }
      }
      child = child.nextSibling
    }
  }

  override fun afterCheckRootProject(context: Context) {
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AaptCrash",
      briefDescription = "Potential AAPT crash",
      explanation = "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
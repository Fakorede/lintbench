package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  override fun beforeCheckRootProject(context: Context) {
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("style")
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return null
  }

  override fun visitElement(context: XmlContext, element: Element) {
    if (element.tagName != "style") {
      return
    }

    val items = element.getElementsByTagName("item")
    for (i in 0 until items.length) {
      val item = items.item(i) as? Element ?: continue
      if (item.getAttribute("name") != "android:id") {
        continue
      }

      val value = item.textContent?.trim() ?: continue
      if (value.startsWith("@+id/")) {
        val nameAttr = item.getAttributeNode("name")
        val location = nameAttr?.let { context.getValueLocation(it) }
          ?: context.getElementLocation(item)
        val scopeNode = nameAttr ?: item
        context.report(
          ISSUE,
          scopeNode,
          location,
          "Defining a style which sets `android:id` to a dynamically generated id can " +
            "cause many versions of `aapt` to crash. To work around this, declare the id " +
            "explicitly with `<item type=\"id\" name=\"...\" />` instead."
        )
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AaptCrash",
      briefDescription = "Potential AAPT crash",
      explanation = "Defining a style which sets `android:id` to a dynamically generated id can " +
        "cause many versions of `aapt`, the resource packaging tool, to crash. To work around " +
        "this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
      category = Category.CORRECTNESS,
      priority = 10,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
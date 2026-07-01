package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  private val declaredIds = mutableSetOf<String>()
  private val dynamicIds = mutableListOf<Pair<String, Location>>()

  override fun beforeCheckRootProject(context: Context) {
    declaredIds.clear()
    dynamicIds.clear()
  }

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("item")
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf("name")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.tagName != "item") {
      return
    }
    val type = element.getAttribute("type")
    if (type == "id") {
      val name = element.getAttribute("name")
      if (name.isNotEmpty()) {
        declaredIds.add(name)
      }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val element = attribute.ownerElement ?: return
    if (element.tagName != "item") {
      return
    }
    if (attribute.value != "android:id") {
      return
    }
    if (!isInsideStyle(element)) {
      return
    }
    val text = element.textContent?.trim() ?: return
    if (!text.startsWith("@+id/")) {
      return
    }
    val id = text.substring("@+id/".length)
    if (id.isEmpty()) {
      return
    }
    dynamicIds.add(id to context.getLocation(attribute))
  }

  override fun afterCheckRootProject(context: Context) {
    for ((id, location) in dynamicIds) {
      if (!declaredIds.contains(id)) {
        context.report(
          ISSUE,
          location,
          "Potential AAPT crash: defining a style which sets `android:id` to a " +
            "dynamically generated id can cause many versions of `aapt` to crash. " +
            "Declare the id explicitly with `<item type=\"id\" name=\"$id\" />` instead."
        )
      }
    }
  }

  private fun isInsideStyle(element: org.w3c.dom.Element): Boolean {
    var parent = element.parentNode
    while (parent != null) {
      if (parent.nodeName == "style") {
        return true
      }
      parent = parent.parentNode
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AaptCrash",
      briefDescription = "Potential AAPT crash",
      explanation = "Defining a style which sets `android:id` to a dynamically generated id " +
        "can cause many versions of `aapt`, the resource packaging tool, to crash. To work " +
        "around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
      category = Category.CORRECTNESS,
      priority = 10,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  override fun beforeCheckRootProject(context: Context) {
    super.beforeCheckRootProject(context)
  }

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String>? {
    return listOf("item")
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return listOf("name")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.tagName == "item" && element.parentNode?.nodeName == "style") {
      val name = element.getAttribute("name")
      if (name == "android:id") {
        val text = element.textContent ?: ""
        if (text.trim().startsWith("@+id/")) {
          context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead."
          )
        }
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    super.afterCheckRootProject(context)
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    // Avoid double reporting since the issue is handled in visitElement
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AaptCrash",
      briefDescription = "Potential AAPT crash",
      explanation = "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
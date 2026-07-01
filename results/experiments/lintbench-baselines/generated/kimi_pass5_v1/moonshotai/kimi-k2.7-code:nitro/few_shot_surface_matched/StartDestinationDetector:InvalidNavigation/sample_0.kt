package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.NAVIGATION
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("navigation")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val androidUri = "http://schemas.android.com/apk/res/android"
    val appUri = "http://schemas.android.com/apk/res-auto"

    val startDestAttr = element.getAttributeNodeNS(appUri, "startDestination")
    if (startDestAttr == null || startDestAttr.value.isEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "No start destination specified"
      )
      return
    }

    val destinationName = extractResourceName(startDestAttr.value) ?: startDestAttr.value

    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child !is org.w3c.dom.Element) {
        continue
      }
      val id = child.getAttributeNS(androidUri, "id")
      if (id.isEmpty()) {
        continue
      }
      if (extractResourceName(id) == destinationName) {
        return
      }
    }

    context.report(
      ISSUE,
      startDestAttr,
      context.getValueLocation(startDestAttr),
      "The start destination must be a direct child of this <navigation>"
    )
  }

  private fun extractResourceName(value: String): String? {
    val index = value.lastIndexOf('/')
    return if (index != -1 && index < value.length - 1) {
      value.substring(index + 1)
    } else {
      null
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "InvalidNavigation",
      briefDescription = "No start destination specified",
      explanation = "All <navigation> elements must have a start destination specified, " +
        "and it must be a direct child of that <navigation>.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        StartDestinationDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}
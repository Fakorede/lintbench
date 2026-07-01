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
    return folderType == com.android.resources.ResourceFolderType.XML
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("navigation")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.localName != "navigation") {
      return
    }

    val startDestinationAttr = element.getAttributeNodeNS(
      "http://schemas.android.com/apk/res-auto",
      "startDestination"
    )
    if (startDestinationAttr == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "No start destination specified"
      )
      return
    }

    val startDestination = startDestinationAttr.value
    if (startDestination.isBlank()) {
      context.report(
        ISSUE,
        startDestinationAttr,
        context.getValueLocation(startDestinationAttr),
        "No start destination specified"
      )
      return
    }

    val children = element.childNodes
    var found = false
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val childElement = child as org.w3c.dom.Element
        val idAttr = childElement.getAttributeNodeNS(
          "http://schemas.android.com/apk/res/android",
          "id"
        )
        if (idAttr != null && idNamesMatch(idAttr.value, startDestination)) {
          found = true
          break
        }
      }
    }

    if (!found) {
      context.report(
        ISSUE,
        startDestinationAttr,
        context.getValueLocation(startDestinationAttr),
        "The start destination must be a direct child of the navigation element"
      )
    }
  }

  private fun idNamesMatch(id1: String, id2: String): Boolean {
    val name1 = id1.substringAfterLast('/', "")
    val name2 = id2.substringAfterLast('/', "")
    return name1.isNotEmpty() && name1 == name2
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
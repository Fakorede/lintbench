package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val TAG_NAVIGATION = "navigation"
private const val ATTR_START_DESTINATION = "startDestination"
private const val ATTR_ID = "id"
private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val APP_URI = "http://schemas.android.com/apk/res-auto"

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.NAVIGATION
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_NAVIGATION)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attr = element.getAttributeNodeNS(APP_URI, ATTR_START_DESTINATION)
    if (attr == null || attr.value.isNullOrBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "No start destination specified"
      )
      return
    }

    val startDest = normalizeId(attr.value)
    if (startDest.isEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "No start destination specified"
      )
      return
    }

    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
      val child = childNodes.item(i)
      if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
        continue
      }
      val childElement = child as org.w3c.dom.Element
      val childId = normalizeId(childElement.getAttributeNS(ANDROID_URI, ATTR_ID))
      if (childId == startDest) {
        return
      }
    }

    context.report(
      ISSUE,
      attr,
      context.getValueLocation(attr),
      "Start destination must be a direct child of this `<navigation>` element"
    )
  }

  private fun normalizeId(value: String): String {
    val v = value.trim()
    val index = v.lastIndexOf('/')
    return if (index != -1) v.substring(index + 1) else v
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "InvalidNavigation",
      briefDescription = "No start destination specified",
      explanation = "All `<navigation>` elements must have a start destination specified, " +
        "and it must be a direct child of that `<navigation>`.",
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
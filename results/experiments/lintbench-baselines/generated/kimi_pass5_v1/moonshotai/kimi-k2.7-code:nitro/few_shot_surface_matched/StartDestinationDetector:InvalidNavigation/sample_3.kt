package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

private const val TAG_NAVIGATION = "navigation"
private const val ATTR_START_DESTINATION = "startDestination"
private const val ATTR_ID = "id"
private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.NAVIGATION
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_NAVIGATION)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val startDestAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_START_DESTINATION)
    if (startDestAttr == null) {
      context.report(
        ISSUE,
        element,
        context.getLocation(element),
        "No start destination specified"
      )
      return
    }

    val startDestValue = startDestAttr.value ?: ""
    val startDestName = extractIdName(startDestValue)
    if (startDestName.isNullOrEmpty()) {
      context.report(
        ISSUE,
        startDestAttr,
        context.getValueLocation(startDestAttr),
        "Invalid start destination"
      )
      return
    }

    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i) as? Element ?: continue
      val childIdAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID) ?: continue
      if (extractIdName(childIdAttr.value) == startDestName) {
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

  private fun extractIdName(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    val index = value.indexOf("id/")
    return if (index >= 0) {
      value.substring(index + 3).takeIf { it.isNotEmpty() }
    } else {
      value.takeIf { it.isNotEmpty() }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "InvalidNavigation",
      briefDescription = "No start destination specified",
      explanation = "All <navigation> elements must have a start destination " +
        "specified via app:startDestination, and the destination must be a " +
        "direct child of that <navigation>.",
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
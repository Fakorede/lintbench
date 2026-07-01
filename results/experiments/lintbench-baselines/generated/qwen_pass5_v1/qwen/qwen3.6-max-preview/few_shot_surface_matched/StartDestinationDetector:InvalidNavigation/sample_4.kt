package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.NAVIGATION
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("navigation")
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val startDestAttr = element.getAttributeNodeNS(AUTO_URI, "startDestination")
    if (startDestAttr == null) {
      context.report(
        ISSUE,
        element,
        context.getLocation(element),
        "No start destination specified"
      )
      return
    }

    val destValue = startDestAttr.value
    val destId = extractIdName(destValue) ?: return

    var found = false
    var child: Element? = XmlUtils.getFirstSubTag(element)
    while (child != null) {
      val childId = child.getAttributeNS(ANDROID_URI, ATTR_ID)
      if (childId.isNotEmpty()) {
        val childIdName = extractIdName(childId)
        if (destId == childIdName) {
          found = true
          break
        }
      }
      child = XmlUtils.getNextTag(child)
    }

    if (!found) {
      context.report(
        ISSUE,
        startDestAttr,
        context.getValueLocation(startDestAttr),
        "Start destination is not a direct child of this <navigation> element"
      )
    }
  }

  private fun extractIdName(value: String): String? {
    return when {
      value.startsWith("@+id/") -> value.substring(5)
      value.startsWith("@id/") -> value.substring(4)
      else -> null
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
      implementation = Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
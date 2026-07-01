package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.NAVIGATION
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(com.android.SdkConstants.TAG_NAVIGATION)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val autoUri = com.android.SdkConstants.AUTO_URI
    val startDestAttr = com.android.SdkConstants.ATTR_START_DESTINATION
    val attr = element.getAttributeNodeNS(autoUri, startDestAttr)
    if (attr == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "No start destination specified for this navigation"
      )
      return
    }

    val targetName = attr.value.toResourceName()
    if (targetName == null || targetName.isEmpty()) {
      context.report(
        ISSUE,
        attr,
        context.getLocation(attr),
        "Invalid start destination reference"
      )
      return
    }

    val androidUri = com.android.SdkConstants.ANDROID_URI
    val idAttr = com.android.SdkConstants.ATTR_ID
    var found = false
    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i) as? org.w3c.dom.Element ?: continue
      val childId = child.getAttributeNS(androidUri, idAttr).toResourceName()
      if (targetName == childId) {
        found = true
        break
      }
    }

    if (!found) {
      context.report(
        ISSUE,
        attr,
        context.getLocation(attr),
        "The startDestination must refer to a direct child of this navigation"
      )
    }
  }

  private fun String.toResourceName(): String? {
    val slash = lastIndexOf('/')
    return if (slash != -1) substring(slash + 1) else this
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "InvalidNavigation",
      briefDescription = "No start destination specified",
      explanation = "All <navigation> elements must specify a start destination " +
        "using the app:startDestination attribute, and the referenced destination " +
        "must be a direct child of that <navigation> element.",
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
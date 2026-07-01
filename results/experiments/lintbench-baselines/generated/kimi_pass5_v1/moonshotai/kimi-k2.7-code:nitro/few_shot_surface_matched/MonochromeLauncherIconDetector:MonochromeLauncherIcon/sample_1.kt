package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_ADAPTIVE_ICON
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.MIPMAP
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_ADAPTIVE_ICON)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    if (!element.hasMonochromeChild()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Monochrome icon is not defined: add a <monochrome> layer to this adaptive icon."
      )
    }
  }

  private fun Element.hasMonochromeChild(): Boolean {
    val children = childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "monochrome") {
        return true
      }
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MonochromeLauncherIcon",
      briefDescription = "Monochrome icon is not defined",
      explanation = "The system may use the coloring of the user's chosen wallpaper and theme " +
        "to tint app icons. Providing a `<monochrome>` layer (which will be used for tinting) " +
        "for every adaptive icon is strongly recommended, otherwise Android 16 QPR 2 and above " +
        "will simply tint the color version of the icon, which may look unusual. Devices running " +
        "earlier Android versions will (with no monochrome layer) show the untinted color icon " +
        "for your app, which will look inconsistent.",
      category = Category.ICONS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        MonochromeLauncherIconDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}
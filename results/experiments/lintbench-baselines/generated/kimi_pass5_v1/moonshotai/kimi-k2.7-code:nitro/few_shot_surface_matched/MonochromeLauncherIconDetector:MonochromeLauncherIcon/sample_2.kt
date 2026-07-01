package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.MIPMAP
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf("adaptive-icon")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE && child.nodeName == "monochrome") {
        return
      }
    }

    context.report(
      ISSUE,
      element,
      context.getNameLocation(element),
      "This adaptive launcher icon is missing a `<monochrome>` layer; " +
        "providing one is strongly recommended so the icon can be tinted consistently " +
        "with the user's chosen wallpaper and theme."
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MonochromeLauncherIcon",
      briefDescription = "Monochrome icon layer missing",
      explanation = "The system may use the coloring of the user's chosen wallpaper and " +
        "theme to tint app icons. Providing a `<monochrome>` layer (which will be used for " +
        "tinting) for every adaptive icon is strongly recommended, otherwise Android 16 QPR 2 " +
        "and above will simply tint the color version of the icon, which may look unusual. " +
        "Devices running earlier Android versions will (with no monochrome layer) show the " +
        "untinted color icon for your app, which will look inconsistent.",
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
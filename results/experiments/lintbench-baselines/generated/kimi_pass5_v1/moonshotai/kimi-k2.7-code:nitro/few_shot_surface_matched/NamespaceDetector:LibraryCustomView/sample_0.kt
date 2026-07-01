package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    if (!context.project.isLibrary) {
      return
    }
    val root = document.documentElement ?: return
    checkElement(context, root)
  }

  private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      val name = attr.nodeName
      if (name.startsWith("xmlns:")) {
        val uri = attr.nodeValue ?: continue
        if (uri.startsWith("http://schemas.android.com/apk/res/") && uri != AUTO_URI) {
          context.report(
            ISSUE,
            attr,
            context.getValueLocation(attr),
            "When using a custom view in a library project, you must use the res-auto namespace, not `$uri`"
          )
        }
      }
    }

    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        checkElement(context, child as org.w3c.dom.Element)
      }
      child = child.nextSibling
    }
  }

  companion object {
    private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Custom views in libraries should use the res-auto namespace",
      explanation = "When using a custom view with custom attributes in a library project, the " +
        "layout must use the special namespace http://schemas.android.com/apk/res-auto instead of " +
        "a URI which includes the library project's own package. This will be used to automatically " +
        "adjust the namespace of the attributes when the library resources are merged into the " +
        "application project.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
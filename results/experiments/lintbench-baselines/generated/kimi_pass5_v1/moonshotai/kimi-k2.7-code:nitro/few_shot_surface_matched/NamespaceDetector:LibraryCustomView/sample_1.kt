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
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val RES_AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val NAMESPACE_PREFIX = "http://schemas.android.com/apk/res/"

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean =
    folderType == ResourceFolderType.LAYOUT

  override fun visitDocument(context: XmlContext, document: Document) {
    if (!context.project.isLibrary) {
      return
    }

    val pkg = context.project.`package` ?: return
    if (pkg.isEmpty()) {
      return
    }

    val packageNamespace = NAMESPACE_PREFIX + pkg
    val root = document.documentElement ?: return
    checkElement(context, root, packageNamespace)
  }

  private fun checkElement(context: XmlContext, element: Element, packageNamespace: String) {
    if (element.tagName.contains('.')) {
      for (i in 0 until element.attributes.length) {
        val attr = element.attributes.item(i) as? Attr ?: continue
        if (attr.namespaceURI == packageNamespace) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "When using a custom view with custom attributes in a library project, " +
              "the layout must use the $RES_AUTO_URI namespace instead of a URI that " +
              "includes the library project's own package."
          )
          break
        }
      }
    }

    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType == Node.ELEMENT_NODE) {
        checkElement(context, child as Element, packageNamespace)
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Library Custom Views Should Use Res-Auto Namespace",
      explanation = "When using a custom view with custom attributes in a library project, " +
        "the layout must use the special res-auto namespace instead of a URI which includes " +
        "the library project's own package. This allows the build system to automatically " +
        "adjust the namespace of the attributes when the library resources are merged into " +
        "the application project.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
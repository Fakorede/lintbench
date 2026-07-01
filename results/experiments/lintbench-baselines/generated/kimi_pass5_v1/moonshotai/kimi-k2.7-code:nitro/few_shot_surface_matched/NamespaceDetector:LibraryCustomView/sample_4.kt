package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.LAYOUT
  }

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    val project = context.project
    if (!project.isLibrary) {
      return
    }

    val pkg = project.getPackage() ?: return
    val expectedUri = "http://schemas.android.com/apk/res/$pkg"

    val root = document.documentElement ?: return
    val attributes = root.attributes

    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      if (attr.namespaceURI != XMLNS_URI) {
        continue
      }

      val value = attr.value
      if (value == expectedUri && value != AUTO_URI && value != ANDROID_URI) {
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr),
          "When using a custom view with custom attributes in a library project, " +
            "the layout must use the res-auto namespace (" + AUTO_URI +
            ") instead of a namespace which includes the library project's own package."
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Custom views in libraries should use res-auto-namespace",
      explanation = "When using a custom view with custom attributes in a library project, " +
        "the layout must use the special namespace " + AUTO_URI +
        " instead of a URI which includes the library project's own package. " +
        "This will be used to automatically adjust the namespace of the attributes " +
        "when the library resources are merged into the application project.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
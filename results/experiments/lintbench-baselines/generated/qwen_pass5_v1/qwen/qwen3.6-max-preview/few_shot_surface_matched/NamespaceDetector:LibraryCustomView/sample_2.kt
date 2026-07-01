package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.SdkConstants.XMLNS_URI
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

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun visitDocument(context: XmlContext, document: Document) {
    if (!context.project.isLibrary) return

    val root = document.documentElement ?: return
    checkElement(context, root)
  }

  private fun checkElement(context: XmlContext, element: Element) {
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as Attr
      val name = attr.name
      val namespace = attr.namespaceURI
      if (namespace == XMLNS_URI || name.startsWith(XMLNS_PREFIX)) {
        val value = attr.value
        if (value.startsWith(PREFIX_RESOURCE_REF) && value != AUTO_URI && value != ANDROID_URI) {
          context.report(
            ISSUE,
            attr,
            context.getLocation(attr),
            "Custom views in libraries should use res-auto-namespace"
          )
        }
      }
    }

    var child: Node? = element.firstChild
    while (child != null) {
      if (child is Element) {
        checkElement(context, child)
      }
      child = child.nextSibling
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Custom views in libraries should use res-auto-namespace",
      explanation = "When using a custom view with custom attributes in a library project, " +
        "the layout must use the special namespace http://schemas.android.com/apk/res-auto " +
        "instead of a URI which includes the library project's own package. This will be " +
        "used to automatically adjust the namespace of the attributes when the library " +
        "resources are merged into the application project.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
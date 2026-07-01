package com.android.tools.lint.checks

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
    val root = document.documentElement ?: return
    checkElement(context, root)
  }

  private fun checkElement(context: XmlContext, element: Element) {
    if (element.namespaceURI?.isHardcodedPackage() == true) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Should use \"$AUTO_URI\" instead of \"${element.namespaceURI}\""
      )
    }

    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as Attr
      if (attr.isNamespaceDeclaration()) {
        if (attr.value.isHardcodedPackage()) {
          context.report(
            ISSUE,
            attr,
            context.getValueLocation(attr),
            "Should use \"$AUTO_URI\" instead of \"${attr.value}\""
          )
        }
      } else if (attr.namespaceURI?.isHardcodedPackage() == true) {
        context.report(
          ISSUE,
          attr,
          context.getLocation(attr),
          "Should use \"$AUTO_URI\" instead of \"${attr.namespaceURI}\""
        )
      }
    }

    var child: Node? = element.firstChild
    while (child != null) {
      if (child.nodeType == Node.ELEMENT_NODE) {
        checkElement(context, child as Element)
      }
      child = child.nextSibling
    }
  }

  private fun String.isHardcodedPackage(): Boolean =
    startsWith(OLD_PREFIX) && this != AUTO_URI && this != ANDROID_URI

  private fun Attr.isNamespaceDeclaration(): Boolean =
    name == "xmlns" || name.startsWith("xmlns:")

  companion object {
    private const val OLD_PREFIX = "http://schemas.android.com/apk/res/"
    private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

    @JvmField
    val ISSUE = Issue.create(
      id = "ResAuto",
      briefDescription = "Hardcoded Package in Namespace",
      explanation = "In Gradle projects, the actual package used in the final APK can vary; " +
        "for example, you can add a `.debug` package suffix in one version and not the other. " +
        "Therefore, you should not hardcode the application package in the resource; instead, " +
        "use the special namespace http://schemas.android.com/apk/res-auto which will cause " +
        "the tools to figure out the right namespace for the resource regardless of the actual " +
        "package used during the build.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(
        NamespaceDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}
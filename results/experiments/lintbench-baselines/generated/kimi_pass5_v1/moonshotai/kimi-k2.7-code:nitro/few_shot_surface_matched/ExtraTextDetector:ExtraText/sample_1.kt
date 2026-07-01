package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ExtraTextDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(
    folderType: com.android.resources.ResourceFolderType,
    fileName: String
  ): Boolean {
    return folderType != com.android.resources.ResourceFolderType.VALUES
  }

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    val root = document.documentElement ?: return
    checkNode(context, root)
  }

  private fun checkNode(context: XmlContext, node: org.w3c.dom.Node) {
    when (node.nodeType) {
      org.w3c.dom.Node.TEXT_NODE,
      org.w3c.dom.Node.CDATA_SECTION_NODE -> {
        if (!node.nodeValue.isNullOrBlank()) {
          context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Extraneous text in resource files"
          )
        }
      }
      org.w3c.dom.Node.ELEMENT_NODE -> {
        var child = node.firstChild
        while (child != null) {
          checkNode(context, child)
          child = child.nextSibling
        }
      }
      else -> {
        // Ignore comments, processing instructions, etc.
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ExtraText",
      briefDescription = "Extraneous text in resource files",
      explanation = "Non-value resource files should only contain elements and attributes. " +
        "Any XML text content found in the file is likely accidental (and potentially dangerous " +
        "if the text resembles XML and the developer believes the text to be functional).",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(
        ExtraTextDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}
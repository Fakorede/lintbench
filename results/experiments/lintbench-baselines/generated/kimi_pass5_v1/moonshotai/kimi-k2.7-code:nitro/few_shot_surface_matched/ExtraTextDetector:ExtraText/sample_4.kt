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
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType != ResourceFolderType.VALUES
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    checkNode(context, document)
  }

  private fun checkNode(context: XmlContext, node: Node) {
    if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
      val text = node.nodeValue?.trim().orEmpty()
      if (text.isNotEmpty()) {
        context.report(
          ISSUE,
          node,
          context.getLocation(node),
          "Extraneous text in resource file: \"$text\""
        )
      }
    }

    var child = node.firstChild
    while (child != null) {
      checkNode(context, child)
      child = child.nextSibling
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ExtraText",
      briefDescription = "Extraneous text in resource files",
      explanation = """
        Non-value resource files should only contain elements and attributes. Any XML text content
        found in the file is likely accidental (and potentially dangerous if the text resembles
        XML and the developer believes the text to be functional).
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.ERROR,
      implementation = Implementation(
        ExtraTextDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}
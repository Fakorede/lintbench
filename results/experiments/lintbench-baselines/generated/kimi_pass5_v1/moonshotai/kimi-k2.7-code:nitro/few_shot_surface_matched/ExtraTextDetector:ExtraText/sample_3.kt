package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ExtraTextDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType != com.android.resources.ResourceFolderType.VALUES
  }

  override fun visitDocument(context: XmlContext) {
    checkNode(context, context.document)
  }

  private fun checkNode(context: XmlContext, node: org.w3c.dom.Node) {
    val value = node.nodeValue
    if (value != null && value.isNotBlank() &&
        (node.nodeType == org.w3c.dom.Node.TEXT_NODE ||
            node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE)) {
      context.report(
        ISSUE,
        node,
        context.getLocation(node),
        "Extraneous text in resource files"
      )
    }

    val children = node.childNodes
    for (i in 0 until children.length) {
      checkNode(context, children.item(i))
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ExtraText",
      briefDescription = "Extraneous text in resource files",
      explanation = "Non-value resource files should only contain elements and attributes. " +
        "Any XML text content found in the file is likely accidental (and potentially " +
        "dangerous if the text resembles XML and the developer believes the text to be " +
        "functional).",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.ERROR,
      implementation = Implementation(ExtraTextDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}
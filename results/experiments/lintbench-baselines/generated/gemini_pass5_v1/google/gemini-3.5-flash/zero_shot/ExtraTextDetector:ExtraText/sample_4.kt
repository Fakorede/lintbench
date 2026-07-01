package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            checkNode(document, context)
        }
    }

    private fun checkNode(node: Node, context: XmlContext) {
        val nodeType = node.nodeType
        if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            val text = node.nodeValue
            if (text != null && text.trim().isNotEmpty()) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Extraneous text inside resource file"
                )
                return
            }
        }
        val children = node.childNodes
        if (children != null) {
            for (i in 0 until children.length) {
                checkNode(children.item(i), context)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "ExtraText",
            "Extraneous text in resource files",
            "Non-value resource files should only contain elements and attributes. Any XML " +
                "text content found in the file is likely accidental (and potentially dangerous " +
                "if the text resembles XML and the developer believes the text to be functional).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
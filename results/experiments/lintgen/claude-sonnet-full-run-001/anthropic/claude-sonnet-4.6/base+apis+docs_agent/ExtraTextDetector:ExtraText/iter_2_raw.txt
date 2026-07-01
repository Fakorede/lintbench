package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. \
                Any XML text content found in the file is likely accidental (and potentially \
                dangerous if the text resembles XML and the developer believes the text to \
                be functional).
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        // Apply to all folder types including manifests (non-values)
        return folderType != ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        val nodeType = node.nodeType
        if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            val text = node.nodeValue
            if (text != null && text.isNotBlank()) {
                val trimmed = text.trim()
                val message = "Unexpected text found in layout file: \"$trimmed\""
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }

        val children = node.childNodes
        if (children != null) {
            for (i in 0 until children.length) {
                checkNode(context, children.item(i))
            }
        }
    }

    // Also handle manifest files which are not resource files
    override fun getApplicableElements(): Collection<String>? = null
}
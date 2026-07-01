package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. Any XML text \
                content found in the file is likely accidental (and potentially dangerous if the \
                text resembles XML and the developer believes the text to be functional).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        val nodeType = node.nodeType
        if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            val text = node.nodeValue
            if (text != null && text.trim().isNotEmpty()) {
                val trimmed = text.trim()
                val display = if (trimmed.length > 20) trimmed.substring(0, 17) + "..." else trimmed
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Extraneous text in resource file: \"$display\""
                )
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            checkNode(context, children.item(i))
        }
    }
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Node
import org.w3c.dom.NodeList

class ExtraTextDetector : ResourceXmlDetector() {

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
        return folderType != ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        checkNodeList(context, document.childNodes)
    }

    private fun checkNodeList(context: XmlContext, nodes: NodeList) {
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            checkNode(context, node)
        }
    }

    private fun checkNode(context: XmlContext, node: Node) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                val text = node.nodeValue
                if (text != null && text.isNotBlank()) {
                    val message = "Unexpected text found in layout file: \"${text.trim()}\""
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
            Node.ELEMENT_NODE -> {
                checkNodeList(context, node.childNodes)
            }
        }
    }
}
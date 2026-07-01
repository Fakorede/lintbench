package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            return
        }
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        when (node.nodeType) {
            Node.TEXT_NODE,
            Node.CDATA_SECTION_NODE -> {
                val text = node.nodeValue ?: ""
                if (text.isNotBlank()) {
                    context.report(
                        ISSUE,
                        context.getLocation(node),
                        "Extraneous text in resource file: \"${text.trim()}\""
                    )
                }
            }
            Node.ELEMENT_NODE,
            Node.DOCUMENT_NODE -> {
                val children = node.childNodes
                for (i in 0 until children.length) {
                    checkNode(context, children.item(i))
                }
            }
            else -> {
                // Comments, processing instructions, DOCTYPE, etc. are ignored.
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = "Non-value resource files should only contain elements and attributes. " +
                    "Any XML text content found in the file is likely accidental (and potentially " +
                    "dangerous if the text resembles XML and the developer believes the text to be functional).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
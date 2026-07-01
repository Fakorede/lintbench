package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. Any XML text \
                content found in the file is likely accidental (and potentially dangerous if the \
                text resembles XML and the developer believes the text to be functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        if (folderType == ResourceFolderType.VALUES) {
            return
        }
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        val type = node.nodeType
        if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
            val text = node.nodeValue
            if (text != null && text.any { !it.isWhitespace() }) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Extraneous text in resource files is not allowed"
                )
            }
        }
        var child = node.firstChild
        while (child != null) {
            checkNode(context, child)
            child = child.nextSibling
        }
    }
}
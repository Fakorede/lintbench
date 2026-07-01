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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.VALUES && folderType != ResourceFolderType.RAW
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType
        if (folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.RAW) {
            return
        }

        val root = document.documentElement ?: return
        checkNode(context, root)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        val childNodes = node.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            val nodeType = child.nodeType
            if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue
                if (text != null && text.trim().isNotEmpty()) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Extraneous text in resource files is not allowed"
                    )
                }
            } else if (nodeType == Node.ELEMENT_NODE) {
                checkNode(context, child)
            }
        }
    }

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
}
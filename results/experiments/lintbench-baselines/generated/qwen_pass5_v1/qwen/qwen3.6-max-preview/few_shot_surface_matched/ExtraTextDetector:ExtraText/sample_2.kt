package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFile
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Node

class ExtraTextDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(context: XmlContext, file: ResourceFile): Boolean {
        return file.folderType != ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkNode(context, root)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val text = child.nodeValue
                    if (!text.isNullOrBlank()) {
                        context.report(
                            ISSUE,
                            context.getLocation(child),
                            "Unexpected text found in layout file: \"$text\""
                        )
                    }
                }
                Node.ELEMENT_NODE -> checkNode(context, child)
            }
            child = child.nextSibling
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
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(ExtraTextDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
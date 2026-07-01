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
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        var child = node.firstChild
        while (child != null) {
            val nodeType = child.nodeType
            if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue
                if (text != null && text.isNotBlank()) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Unexpected text found in layout file: \"${text.trim()}\""
                    )
                }
            } else if (nodeType == Node.ELEMENT_NODE) {
                checkNode(context, child)
            }
            child = child.nextSibling
        }
    }
}
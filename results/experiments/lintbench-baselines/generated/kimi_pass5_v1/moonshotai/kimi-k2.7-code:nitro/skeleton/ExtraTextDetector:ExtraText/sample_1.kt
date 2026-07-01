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
        private val IMPLEMENTATION = Implementation(
            ExtraTextDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. Any XML text
                content found in the file is likely accidental, and can be dangerous if the text
                resembles XML and the developer believes the text to be functional.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType != ResourceFolderType.VALUES

    override fun visitDocument(context: XmlContext, document: Document) {
        checkChildren(context, document)
    }

    private fun checkChildren(context: XmlContext, node: Node) {
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE,
                Node.CDATA_SECTION_NODE -> {
                    val text = child.nodeValue
                    if (text != null && !text.isBlank()) {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Unexpected text content in resource file: \"${text.trim()}\""
                        )
                    }
                }
                Node.ELEMENT_NODE -> checkChildren(context, child)
            }
            child = child.nextSibling
        }
    }
}
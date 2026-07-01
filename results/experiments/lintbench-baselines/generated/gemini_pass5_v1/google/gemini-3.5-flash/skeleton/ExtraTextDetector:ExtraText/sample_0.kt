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
                Non-value resource files should only contain elements and attributes. Any XML text content found \
                in the file is likely accidental (and potentially dangerous if the text resembles XML and the \
                developer believes the text to be functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkNode(context, root)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        var child = node.firstChild
        while (child != null) {
            val type = child.nodeType
            if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue
                if (text != null && text.isNotBlank()) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Extraneous text in resource files is not allowed"
                    )
                }
            } else if (type == Node.ELEMENT_NODE) {
                checkNode(context, child)
            }
            child = child.nextSibling
        }
    }
}
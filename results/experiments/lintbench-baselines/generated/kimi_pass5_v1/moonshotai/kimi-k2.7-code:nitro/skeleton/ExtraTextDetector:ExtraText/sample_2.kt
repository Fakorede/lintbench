package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
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
                Non-value resource files should only contain elements and attributes.
                Any XML text content found in the file is likely accidental (and potentially
                dangerous if the text resembles XML and the developer believes the text to be
                functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.VALUES && folderType != ResourceFolderType.RAW
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        checkNode(context, document)
    }

    private fun checkNode(context: XmlContext, node: Node) {
        if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            val text = node.nodeValue ?: ""
            if (!text.isBlank()) {
                val message = "Unexpected text found in resource file: \"${text.trim()}\""
                context.report(ISSUE, getLocation(context, node), message)
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            checkNode(context, children.item(i))
        }
    }

    private fun getLocation(context: XmlContext, node: Node): Location {
        val offsets = context.parser.getNodeOffsets(context.file, node)
        return if (offsets != null) {
            Location.create(context.file, offsets.first, offsets.second)
        } else {
            Location.create(context.file)
        }
    }
}
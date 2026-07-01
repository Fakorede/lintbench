package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ExtraTextDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                reportExtraText(context, child)
            }
        }
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val children = document.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                reportExtraText(context, child)
            }
        }
    }

    private fun reportExtraText(context: XmlContext, node: Node) {
        val text = node.nodeValue ?: return
        if (text.isBlank()) return
        context.report(
            ISSUE_EXTRA_TEXT,
            node,
            context.getLocation(node),
            "Unexpected text content in resource file: \"$text\""
        )
    }

    companion object {
        @JvmField
        val ISSUE_EXTRA_TEXT = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes.
                Any XML text content found in the file is likely accidental (and potentially
                dangerous if the text resembles XML and the developer believes the text to
                be functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
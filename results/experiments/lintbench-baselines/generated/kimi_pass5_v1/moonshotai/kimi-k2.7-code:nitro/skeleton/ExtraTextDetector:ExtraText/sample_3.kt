package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
                Any XML text content found in the file is likely accidental, and can be
                dangerous if the text resembles XML and the developer believes the text
                to be functional.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType != com.android.resources.ResourceFolderType.VALUES

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        checkChildren(context, document)
    }

    private fun checkChildren(context: XmlContext, node: org.w3c.dom.Node) {
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                org.w3c.dom.Node.TEXT_NODE,
                org.w3c.dom.Node.CDATA_SECTION_NODE -> {
                    if (!child.nodeValue.isBlank()) {
                        context.report(
                            ISSUE,
                            context.getLocation(child),
                            "Unexpected text content in resource file"
                        )
                    }
                }
                else -> checkChildren(context, child)
            }
            child = child.nextSibling
        }
    }
}
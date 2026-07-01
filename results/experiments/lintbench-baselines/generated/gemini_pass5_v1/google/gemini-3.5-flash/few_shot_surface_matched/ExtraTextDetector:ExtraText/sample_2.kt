package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ExtraTextDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType != com.android.resources.ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        checkText(context, root)
    }

    private fun checkText(context: XmlContext, node: org.w3c.dom.Node) {
        var child = node.firstChild
        while (child != null) {
            val nodeType = child.nodeType
            if (nodeType == org.w3c.dom.Node.TEXT_NODE || nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue
                if (text != null && text.trim().isNotEmpty()) {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Extraneous text in resource files is not allowed"
                    )
                }
            } else if (nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkText(context, child)
            }
            child = child.nextSibling
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = "Non-value resource files should only contain elements and attributes. Any XML text content found in the file is likely accidental (and potentially dangerous if the text resembles XML and the developer believes the text to be functional).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
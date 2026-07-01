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
        val ISSUE = Issue.create(
            "ExtraText",
            "Extraneous text in resource files",
            "Non-value resource files should only contain elements and attributes. " +
                "Any XML text content found in the file is likely accidental (and potentially " +
                "dangerous if the text resembles XML and the developer believes the text to be functional).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(ExtraTextDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun visitText(context: XmlContext, node: Node) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            return
        }

        val text = node.nodeValue
        if (!text.isNullOrBlank()) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Extraneous text in resource file"
            )
        }
    }
}
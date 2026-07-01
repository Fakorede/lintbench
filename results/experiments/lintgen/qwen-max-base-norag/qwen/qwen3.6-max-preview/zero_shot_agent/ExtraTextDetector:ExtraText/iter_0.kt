package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Node
import java.util.EnumSet

class ExtraTextDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitText(context: XmlContext, node: Node) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            return
        }

        val text = node.nodeValue ?: return
        if (text.isBlank()) {
            return
        }

        val trimmed = text.trim()
        val displayText = if (trimmed.length > 40) {
            trimmed.substring(0, 40) + "..."
        } else {
            trimmed
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Unexpected text found in resource file: \"$displayText\""
        )
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
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }
}
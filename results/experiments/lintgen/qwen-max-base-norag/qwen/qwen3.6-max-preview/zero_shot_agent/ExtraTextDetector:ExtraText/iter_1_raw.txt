package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ExtraTextDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            return
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                val text = child.nodeValue
                if (!text.isNullOrBlank()) {
                    val trimmed = text.trim()
                    val displayText = if (trimmed.length > 40) {
                        trimmed.substring(0, 40) + "..."
                    } else {
                        trimmed
                    }
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Unexpected text found in resource file: \"$displayText\""
                    )
                }
            }
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
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE
            )
        )
    }
}
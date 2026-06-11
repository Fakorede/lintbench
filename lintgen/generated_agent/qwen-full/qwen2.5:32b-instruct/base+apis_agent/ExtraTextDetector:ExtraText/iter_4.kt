package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ExtraTextDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE_EXTRANEOUS_TEXT = Issue.create(
            id = "ExtraneousText",
            briefDescription = "Extraneous text in resource files",
            explanation = """
                Non-value resource files should only contain elements and attributes. Any XML text content found in the file is likely accidental (and potentially dangerous if the text resembles XML and the developer believes the text to be functional).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("*")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val textContent = element.textContent.trim()
        if (textContent.isNotEmpty() && !element.hasChildNodes()) {
            context.report(
                ISSUE_EXTRANEOUS_TEXT,
                context.getLocation(element),
                "Extraneous text found in resource file"
            )
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    override fun visitDocument(context: XmlContext, document: Document) {
        val textContent = document.textContent.trim()
        if (textContent.isNotEmpty()) {
            context.report(
                ISSUE_EXTRANEOUS_TEXT,
                context.getLocation(document),
                "Extraneous text found in resource file"
            )
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT ||
               folderType == ResourceFolderType.MENU
    }
}
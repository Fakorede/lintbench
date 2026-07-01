package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class ExtraTextDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue
                if (!text.isNullOrBlank()) {
                    context.report(
                        ISSUE,
                        context.getLocation(child),
                        "Unexpected text found in resource file: \"${text.trim()}\""
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ExtraText",
            briefDescription = "Extraneous text in resource files",
            explanation = "Non-value resource files should only contain elements and attributes. Any XML text content found in the file is likely accidental (and potentially dangerous if the text resembles XML and the developer believes the text to be functional).",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExtraTextDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE, Scope.MANIFEST)
            )
        )
    }
}
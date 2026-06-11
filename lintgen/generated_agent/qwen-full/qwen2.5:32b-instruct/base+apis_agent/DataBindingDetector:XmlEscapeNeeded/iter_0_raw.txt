package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE_MISSING_XML_ESCAPE = Issue.create(
            id = "MissingXmlEscape",
            briefDescription = "XML special characters must be escaped",
            explanation = """
                When a string contains characters that have special usage in XML, you must escape the characters.
                This issue reports strings containing unescaped special characters.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("text", "contentDescription")
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return

        if (value.containsAnyOf("<>", "&") && !value.contains("&lt;") && !value.contains("&gt;") && !value.contains("&amp;")) {
            context.report(
                ISSUE_MISSING_XML_ESCAPE,
                context.getLocation(attribute),
                "XML special characters must be escaped"
            )
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }
}
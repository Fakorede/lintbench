package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ChildCountDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AdapterViewChildCount",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter. 
                Having children defined directly in the XML is not supported and will cause issues.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("ListView", "Spinner", "Gallery")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.hasChildNodes()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "AdapterView cannot have children defined in XML"
            )
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    // Other methods can be left empty as they are not needed for this detector.
    override fun visitElementAfter(context: XmlContext, element: Element) {}
    override fun getApplicableAttributes(): Collection<String>? = null
    override fun visitAttribute(context: XmlContext, attribute: Attr) {}
    override fun visitDocument(context: XmlContext, document: Document) {}
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object Issues {
        val DYNAMIC_ID_IN_STYLE = Issue.create(
            id = "DynamicIdInStyle",
            briefDescription = "Potential AAPT crash due to dynamic ID in style",
            explanation = """
                Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type="id" name="..." />` instead.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val typeAttribute = element.getAttributeNode("type")
        if (typeAttribute != null && "id" == typeAttribute.value) {
            val nameAttribute = element.getAttributeNode("name")
            if (nameAttribute != null && isDynamicId(nameAttribute.value)) {
                context.report(
                    DYNAMIC_ID_IN_STYLE,
                    element,
                    context.getLocation(element),
                    "Avoid using dynamically generated IDs in styles to prevent AAPT crashes."
                )
            }
        }
    }

    private fun isDynamicId(id: String): Boolean {
        return id.startsWith("@+id/")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }
}
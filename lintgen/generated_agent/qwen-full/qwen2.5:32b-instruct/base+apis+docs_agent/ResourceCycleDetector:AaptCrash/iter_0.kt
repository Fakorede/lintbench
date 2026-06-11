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
            """.trimIndent(),
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
        return listOf("style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val idAttribute = element.getAttributeNode("android:id")
        if (idAttribute != null && isDynamicId(idAttribute.value)) {
            context.report(
                DYNAMIC_ID_IN_STYLE,
                element,
                context.getLocation(element),
                "Avoid using dynamically generated IDs in styles. Use explicit ID declaration instead."
            )
        }
    }

    private fun isDynamicId(idValue: String): Boolean {
        return idValue.startsWith("@+id/")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.STYLE
    }
}
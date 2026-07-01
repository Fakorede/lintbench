package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.*

class ResourceCycleDetector : Detector(), XmlScanner {
    companion object {
        private val IMPLEMENTATION_XML = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        @JvmField
        val RESOURCE_CYCLE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to runtime exceptions.
                """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION_XML
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.COLOR || folderType == ResourceFolderType.MENU || folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // ...
    }
}
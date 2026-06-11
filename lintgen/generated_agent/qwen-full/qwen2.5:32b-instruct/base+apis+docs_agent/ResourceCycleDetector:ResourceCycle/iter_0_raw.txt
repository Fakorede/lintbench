package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.*

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                Cycles in resource definitions can lead to runtime exceptions. Ensure that there are no circular references.
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

    private val visitedResources: MutableSet<String> = HashSet()
    private val visitingStack: Deque<String> = ArrayDeque()

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode("name")
        if (nameAttr != null && !visitedResources.contains(nameAttr.value)) {
            visitingStack.push(nameAttr.value)
        }
    }

    override fun visitElementAfter(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode("name")
        if (nameAttr != null) {
            val resourceName = nameAttr.value
            visitingStack.pop()

            val referenceAttr = element.getAttributeNode("android:drawable") ?: return

            val referencedResourceName = referenceAttr.value.substringAfter('@')
            if (visitingStack.contains(referencedResourceName)) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Cycle detected in resource definitions"
                )
            } else if (!visitedResources.contains(resourceName)) {
                visitedResources.add(resourceName)
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.DRAWABLE
    }
}
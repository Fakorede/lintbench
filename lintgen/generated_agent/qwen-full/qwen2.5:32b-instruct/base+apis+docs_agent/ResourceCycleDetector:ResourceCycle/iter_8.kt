package com.android.tools.lint.checks

import com.android.SdkConstants.*
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
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }

    private val visitedResources: MutableMap<String, Boolean> = HashMap()
    private val visitingStack: Deque<String> = ArrayDeque()

    override fun getApplicableElements(): Collection<String>? {
        return listOf("item", "style", "drawable", "font", "color", "string")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode(ATTR_NAME)
        if (nameAttr != null && !visitedResources.containsKey(nameAttr.value)) {
            visitingStack.push(nameAttr.value)
        }
    }

    override fun visitElementAfter(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode(ATTR_NAME)
        if (nameAttr != null) {
            val resourceName = nameAttr.value
            visitingStack.pop()

            val referenceAttrs = listOf(
                "android:drawable",
                "parent",
                "item",
                "font",
                "color",
                "string"
            ).map { element.getAttributeNode(it) }.filterNotNull()

            for (referenceAttr in referenceAttrs) {
                val referencedResourceName = referenceAttr.value.substringAfter('@')
                if (visitingStack.contains(referencedResourceName)) {
                    context.report(
                        ISSUE,
                        context.getLocation(element),
                        "Cycle detected in resource definitions"
                    )
                }
            }

            visitedResources[resourceName] = true
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return when (folderType) {
            ResourceFolderType.DRAWABLE,
            ResourceFolderType.FONT,
            ResourceFolderType.COLOR,
            ResourceFolderType.LAYOUT,
            ResourceFolderType.MENU,
            ResourceFolderType.ANIM,
            ResourceFolderType.ANIINTERPOLATOR,
            ResourceFolderType.TRANSITION,
            ResourceFolderType.XML,
            ResourceFolderType.RAW -> true
            else -> false
        }
    }
}
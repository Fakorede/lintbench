package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.ide.common.resources.ResourceUrl
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ResourceCycle = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can \
                lead to runtime exceptions.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    private val graph = mutableMapOf<ResourceUrl, MutableSet<ResourceUrl>>()
    private val resourceLocations = mutableMapOf<ResourceUrl, Location>()

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun beforeCheckRootProject(context: Context) {
        super.beforeCheckRootProject(context)
        graph.clear()
        resourceLocations.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val currentResource = getCurrentResource(context, element) ?: return
        resourceLocations.putIfAbsent(currentResource, context.getLocation(element))

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            val value = attr.value ?: continue
            val reference = parseReference(value) ?: continue
            graph.getOrPut(currentResource) { mutableSetOf() }.add(reference)
        }

        val text = element.textContent?.trim()
        if (!text.isNullOrEmpty()) {
            val reference = parseReference(text)
            if (reference != null) {
                graph.getOrPut(currentResource) { mutableSetOf() }.add(reference)
            }
        }
    }

    private fun parseReference(value: String): ResourceUrl? {
        if (!value.startsWith("@")) return null
        val reference = ResourceUrl.parse(value) ?: return null
        if (reference.packageName != null) return null
        if (reference.isCreate) return null
        if (!isRelevantResourceType(reference.type)) return null
        return reference
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<ResourceUrl>()
        val stack = mutableSetOf<ResourceUrl>()
        val path = mutableListOf<ResourceUrl>()

        fun dfs(node: ResourceUrl) {
            visited.add(node)
            stack.add(node)
            path.add(node)

            for (neighbor in graph[node].orEmpty()) {
                when {
                    neighbor !in visited -> dfs(neighbor)
                    neighbor in stack -> {
                        val cycleStart = path.indexOf(neighbor)
                        reportCycle(context, path.subList(cycleStart, path.size))
                    }
                }
            }

            path.removeAt(path.lastIndex)
            stack.remove(node)
        }

        for (node in graph.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }

    private fun reportCycle(context: Context, cycle: List<ResourceUrl>) {
        if (cycle.isEmpty()) return
        val first = cycle.first()
        val location = resourceLocations[first] ?: return
        val cycleString = buildString {
            for (resource in cycle) {
                append("@${resource.type.getName()}/${resource.name} → ")
            }
            append("@${first.type.getName()}/${first.name}")
        }
        context.report(
            ResourceCycle,
            location,
            "Resource cycle detected: $cycleString"
        )
    }

    private fun getCurrentResource(context: XmlContext, element: Element): ResourceUrl? {
        val folderType = ResourceFolderType.getFolderType(context.file.parentFile) ?: return null
        return when (folderType) {
            ResourceFolderType.VALUES -> findEnclosingValueResource(element)
            else -> {
                val type = folderType.getResourceType() ?: return null
                ResourceUrl.create(type, context.file.nameWithoutExtension, false)
            }
        }
    }

    private fun findEnclosingValueResource(element: Element): ResourceUrl? {
        var current: Element? = element
        while (current != null) {
            val type = VALUE_TAG_TO_TYPE[current.tagName]
            if (type != null) {
                val name = current.getAttribute(SdkConstants.ATTR_NAME)
                if (name.isNotBlank()) {
                    return ResourceUrl.create(type, name, false)
                }
            }
            current = current.parentNode as? Element
        }
        return null
    }

    private fun isRelevantResourceType(type: ResourceType): Boolean {
        return when (type) {
            ResourceType.DRAWABLE,
            ResourceType.LAYOUT,
            ResourceType.MENU,
            ResourceType.COLOR,
            ResourceType.DIMEN,
            ResourceType.STRING,
            ResourceType.STYLE,
            ResourceType.ANIM,
            ResourceType.ANIMATOR,
            ResourceType.TRANSITION,
            ResourceType.MIPMAP,
            ResourceType.ARRAY,
            ResourceType.PLURALS,
            ResourceType.BOOL,
            ResourceType.INTEGER,
            ResourceType.FRACTION,
            ResourceType.ATTR,
            ResourceType.ID -> true
            else -> false
        }
    }
}

private val VALUE_TAG_TO_TYPE = mapOf(
    "string" to ResourceType.STRING,
    "color" to ResourceType.COLOR,
    "dimen" to ResourceType.DIMEN,
    "style" to ResourceType.STYLE,
    "bool" to ResourceType.BOOL,
    "integer" to ResourceType.INTEGER,
    "fraction" to ResourceType.FRACTION,
    "array" to ResourceType.ARRAY,
    "string-array" to ResourceType.ARRAY,
    "integer-array" to ResourceType.ARRAY,
    "plurals" to ResourceType.PLURALS,
    "attr" to ResourceType.ATTR,
    "id" to ResourceType.ID,
    "drawable" to ResourceType.DRAWABLE
)
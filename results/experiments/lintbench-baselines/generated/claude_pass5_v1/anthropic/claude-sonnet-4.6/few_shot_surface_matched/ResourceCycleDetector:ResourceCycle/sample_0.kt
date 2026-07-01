package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.FD_RES_COLOR
import com.android.SdkConstants.FD_RES_DRAWABLE
import com.android.SdkConstants.FD_RES_LAYOUT
import com.android.SdkConstants.FD_RES_VALUES
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.io.File
import java.util.EnumSet

class ResourceCycleDetector : ResourceXmlDetector() {

    // Maps resource type -> (resource name -> list of referenced resource names of the same type)
    private val dependencyGraph = mutableMapOf<ResourceType, MutableMap<String, MutableList<String>>>()

    // Track where each resource is defined for error reporting
    private val resourceLocations = mutableMapOf<ResourceType, MutableMap<String, Pair<XmlContext, org.w3c.dom.Node>>>()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        dependencyGraph.clear()
        resourceLocations.clear()
    }

    override fun appliesTo(folderType: com.android.tools.lint.detector.api.ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_DRAWABLE,
            TAG_ITEM,
            TAG_LAYOUT
        )
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(
            ATTR_PARENT,
            ATTR_STYLE,
            ATTR_LAYOUT,
            ATTR_DRAWABLE,
            ATTR_COLOR
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val tag = element.tagName

        when {
            tag == TAG_STYLE -> {
                val nameAttr = element.getAttributeNode(ATTR_NAME) ?: return
                val name = nameAttr.value ?: return
                val resourceName = name.substringBefore(".")
                val parentAttr = element.getAttribute(ATTR_PARENT)

                if (parentAttr.isNotEmpty()) {
                    val parentName = resolveStyleReference(parentAttr) ?: return
                    addDependency(ResourceType.STYLE, resourceName, parentName, context, element)
                } else {
                    // Implicit parent via dot notation
                    if (name.contains(".")) {
                        val implicitParent = name.substringBeforeLast(".")
                        addDependency(ResourceType.STYLE, name, implicitParent, context, element)
                    }
                }

                // Record location
                recordLocation(ResourceType.STYLE, resourceName, context, element)
            }

            tag == TAG_COLOR && folderType == com.android.tools.lint.detector.api.ResourceFolderType.VALUES -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val text = element.textContent?.trim() ?: return
                val ref = parseResourceReference(text, ResourceType.COLOR) ?: return
                addDependency(ResourceType.COLOR, name, ref, context, element)
                recordLocation(ResourceType.COLOR, name, context, element)
            }

            tag == TAG_DRAWABLE && folderType == com.android.tools.lint.detector.api.ResourceFolderType.VALUES -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val text = element.textContent?.trim() ?: return
                val ref = parseResourceReference(text, ResourceType.DRAWABLE) ?: return
                addDependency(ResourceType.DRAWABLE, name, ref, context, element)
                recordLocation(ResourceType.DRAWABLE, name, context, element)
            }

            tag == TAG_ITEM -> {
                val typeAttr = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
                val resourceType = ResourceType.fromXmlValue(typeAttr) ?: return
                if (resourceType !in listOf(ResourceType.STYLE, ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.LAYOUT)) {
                    return
                }
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val text = element.textContent?.trim() ?: return
                val ref = parseResourceReference(text, resourceType) ?: return
                addDependency(resourceType, name, ref, context, element)
                recordLocation(resourceType, name, context, element)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val folderType = context.resourceFolderType ?: return
        val attrName = attribute.localName ?: attribute.name ?: return
        val value = attribute.value ?: return

        when (attrName) {
            ATTR_PARENT -> {
                // Handled in visitElement for styles
            }
            ATTR_LAYOUT -> {
                if (folderType == com.android.tools.lint.detector.api.ResourceFolderType.LAYOUT) {
                    val ref = parseResourceReference(value, ResourceType.LAYOUT) ?: return
                    val fileName = context.file.nameWithoutExtension
                    addDependency(ResourceType.LAYOUT, fileName, ref, context, attribute.ownerElement)
                    recordLocation(ResourceType.LAYOUT, fileName, context, attribute.ownerElement)
                }
            }
            ATTR_STYLE -> {
                // style references on elements - not typically cycles in the same resource
            }
            ATTR_DRAWABLE -> {
                val ref = parseResourceReference(value, ResourceType.DRAWABLE) ?: return
                if (folderType == com.android.tools.lint.detector.api.ResourceFolderType.DRAWABLE) {
                    val fileName = context.file.nameWithoutExtension
                    addDependency(ResourceType.DRAWABLE, fileName, ref, context, attribute.ownerElement)
                    recordLocation(ResourceType.DRAWABLE, fileName, context, attribute.ownerElement)
                }
            }
            ATTR_COLOR -> {
                val ref = parseResourceReference(value, ResourceType.COLOR) ?: return
                if (folderType == com.android.tools.lint.detector.api.ResourceFolderType.COLOR) {
                    val fileName = context.file.nameWithoutExtension
                    addDependency(ResourceType.COLOR, fileName, ref, context, attribute.ownerElement)
                    recordLocation(ResourceType.COLOR, fileName, context, attribute.ownerElement)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for ((resourceType, graph) in dependencyGraph) {
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()

            for (node in graph.keys) {
                if (node !in visited) {
                    detectCycle(context, resourceType, node, graph, visited, inStack, mutableListOf())
                }
            }
        }
    }

    private fun detectCycle(
        context: com.android.tools.lint.detector.api.Context,
        resourceType: ResourceType,
        node: String,
        graph: Map<String, List<String>>,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>
    ) {
        visited.add(node)
        inStack.add(node)
        path.add(node)

        val neighbors = graph[node] ?: emptyList()
        for (neighbor in neighbors) {
            if (neighbor !in visited) {
                detectCycle(context, resourceType, neighbor, graph, visited, inStack, path)
            } else if (neighbor in inStack) {
                // Found a cycle
                val cycleStart = path.indexOf(neighbor)
                val cycle = path.subList(cycleStart, path.size) + neighbor
                reportCycle(context, resourceType, cycle)
            }
        }

        path.removeAt(path.size - 1)
        inStack.remove(node)
    }

    private fun reportCycle(
        context: com.android.tools.lint.detector.api.Context,
        resourceType: ResourceType,
        cycle: List<String>
    ) {
        val typeStr = resourceType.getName()
        val cycleStr = cycle.joinToString(" -> ")
        val message = "Cycle detected in resource definitions: $typeStr resource cycle: $cycleStr"

        // Try to find a location for the first node in the cycle
        val firstName = cycle.first()
        val locationInfo = resourceLocations[resourceType]?.get(firstName)

        if (locationInfo != null) {
            val (xmlContext, node) = locationInfo
            val location = xmlContext.getLocation(node)
            context.report(ISSUE, location, message)
        } else {
            context.report(ISSUE, com.android.tools.lint.detector.api.Location.create(context.project.dir), message)
        }
    }

    private fun addDependency(
        resourceType: ResourceType,
        from: String,
        to: String,
        context: XmlContext,
        node: org.w3c.dom.Node
    ) {
        val typeGraph = dependencyGraph.getOrPut(resourceType) { mutableMapOf() }
        typeGraph.getOrPut(from) { mutableListOf() }.add(to)
        recordLocation(resourceType, from, context, node)
    }

    private fun recordLocation(
        resourceType: ResourceType,
        name: String,
        context: XmlContext,
        node: org.w3c.dom.Node
    ) {
        val typeMap = resourceLocations.getOrPut(resourceType) { mutableMapOf() }
        if (!typeMap.containsKey(name)) {
            typeMap[name] = Pair(context, node)
        }
    }

    private fun resolveStyleReference(reference: String): String? {
        // Handle @style/Foo, ?style/Foo, or just Foo (parent name)
        return when {
            reference.startsWith("@style/") -> reference.removePrefix("@style/")
            reference.startsWith("@android:style/") -> null // Skip framework styles
            reference.startsWith("?") -> null
            reference.contains(":") -> null // Skip framework/library styles
            reference.isNotEmpty() -> reference
            else -> null
        }
    }

    private fun parseResourceReference(value: String, expectedType: ResourceType): String? {
        if (!value.startsWith("@")) return null
        val url = ResourceUrl.parse(value) ?: return null
        if (url.isFramework) return null
        if (url.type != expectedType) return null
        return url.name
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions at runtime when the resource is resolved.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
        )
    }
}
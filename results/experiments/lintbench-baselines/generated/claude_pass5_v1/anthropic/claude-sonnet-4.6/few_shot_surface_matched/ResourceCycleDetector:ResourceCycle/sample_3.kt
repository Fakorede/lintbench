package com.android.tools.lint.checks

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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceUrl
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    // Maps resource type -> (resource name -> list of referenced resource names of same type)
    private val graph: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf()

    // Maps resource type -> (resource name -> location for reporting)
    private val locations: MutableMap<String, MutableMap<String, Location>> = mutableMapOf()

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
            folderType == ResourceFolderType.LAYOUT ||
            folderType == ResourceFolderType.DRAWABLE ||
            folderType == ResourceFolderType.COLOR
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE, TAG_COLOR, TAG_DRAWABLE, TAG_ITEM, TAG_LAYOUT)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_PARENT, ATTR_LAYOUT, ATTR_DRAWABLE, ATTR_COLOR, ATTR_STYLE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val tag = element.tagName

        when {
            tag == TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val parent = element.getAttribute(ATTR_PARENT).takeIf { it.isNotEmpty() }
                val resourceType = "style"

                // Record the node in the graph
                ensureNode(resourceType, name)
                val loc = context.getNameLocation(element)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(name, loc)

                if (parent != null) {
                    val parentName = stripResourcePrefix(parent, resourceType)
                    if (parentName != null) {
                        addEdge(resourceType, name, parentName)
                    }
                } else {
                    // Implicit parent via dot notation: "ParentStyle.ChildStyle"
                    val dotIndex = name.lastIndexOf('.')
                    if (dotIndex > 0) {
                        val impliedParent = name.substring(0, dotIndex)
                        addEdge(resourceType, name, impliedParent)
                    }
                }
            }

            tag == TAG_COLOR && folderType == ResourceFolderType.VALUES -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val resourceType = "color"
                ensureNode(resourceType, name)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(name, context.getNameLocation(element))
                val text = element.textContent?.trim()
                if (!text.isNullOrEmpty()) {
                    val ref = parseResourceReference(text, resourceType)
                    if (ref != null) {
                        addEdge(resourceType, name, ref)
                    }
                }
            }

            tag == TAG_DRAWABLE && folderType == ResourceFolderType.VALUES -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val resourceType = "drawable"
                ensureNode(resourceType, name)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(name, context.getNameLocation(element))
                val text = element.textContent?.trim()
                if (!text.isNullOrEmpty()) {
                    val ref = parseResourceReference(text, resourceType)
                    if (ref != null) {
                        addEdge(resourceType, name, ref)
                    }
                }
            }

            tag == TAG_ITEM && folderType == ResourceFolderType.VALUES -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val type = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
                ensureNode(type, name)
                locations.getOrPut(type) { mutableMapOf() }.putIfAbsent(name, context.getNameLocation(element))
                val text = element.textContent?.trim()
                if (!text.isNullOrEmpty()) {
                    val ref = parseResourceReference(text, type)
                    if (ref != null) {
                        addEdge(type, name, ref)
                    }
                }
            }

            tag == TAG_LAYOUT && folderType == ResourceFolderType.VALUES -> {
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val resourceType = "layout"
                ensureNode(resourceType, name)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(name, context.getNameLocation(element))
                val text = element.textContent?.trim()
                if (!text.isNullOrEmpty()) {
                    val ref = parseResourceReference(text, resourceType)
                    if (ref != null) {
                        addEdge(resourceType, name, ref)
                    }
                }
            }

            folderType == ResourceFolderType.LAYOUT ||
                folderType == ResourceFolderType.DRAWABLE ||
                folderType == ResourceFolderType.COLOR -> {
                // Handled via visitAttribute for these folder types
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val folderType = context.resourceFolderType ?: return
        val attrName = attribute.localName ?: attribute.name
        val value = attribute.value?.trim() ?: return

        when {
            attrName == ATTR_PARENT && folderType == ResourceFolderType.VALUES -> {
                // Already handled in visitElement for TAG_STYLE
            }

            attrName == ATTR_LAYOUT -> {
                // e.g. layout="@layout/foo" in an include tag or similar
                val ref = parseResourceReference(value, "layout") ?: return
                val resourceType = "layout"
                // Determine the current resource name from the file name
                val currentName = context.file.nameWithoutExtension
                ensureNode(resourceType, currentName)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(currentName, context.getLocation(attribute))
                addEdge(resourceType, currentName, ref)
            }

            attrName == ATTR_DRAWABLE -> {
                val ref = parseResourceReference(value, "drawable") ?: return
                val resourceType = "drawable"
                val currentName = context.file.nameWithoutExtension
                ensureNode(resourceType, currentName)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(currentName, context.getLocation(attribute))
                addEdge(resourceType, currentName, ref)
            }

            attrName == ATTR_COLOR -> {
                val ref = parseResourceReference(value, "color") ?: return
                val resourceType = "color"
                val currentName = context.file.nameWithoutExtension
                ensureNode(resourceType, currentName)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(currentName, context.getLocation(attribute))
                addEdge(resourceType, currentName, ref)
            }

            attrName == ATTR_STYLE -> {
                val ref = parseResourceReference(value, "style") ?: return
                val resourceType = "style"
                val currentName = context.file.nameWithoutExtension
                ensureNode(resourceType, currentName)
                locations.getOrPut(resourceType) { mutableMapOf() }.putIfAbsent(currentName, context.getLocation(attribute))
                addEdge(resourceType, currentName, ref)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // For each resource type, detect cycles using DFS
        for ((resourceType, adjacency) in graph) {
            val typeLocations = locations[resourceType] ?: emptyMap()
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()

            for (node in adjacency.keys) {
                if (node !in visited) {
                    detectCycle(
                        context,
                        resourceType,
                        node,
                        adjacency,
                        typeLocations,
                        visited,
                        inStack,
                        mutableListOf()
                    )
                }
            }
        }
    }

    private fun detectCycle(
        context: Context,
        resourceType: String,
        node: String,
        adjacency: Map<String, List<String>>,
        typeLocations: Map<String, Location>,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>
    ) {
        visited.add(node)
        inStack.add(node)
        path.add(node)

        val neighbors = adjacency[node] ?: emptyList()
        for (neighbor in neighbors) {
            if (neighbor !in visited) {
                if (adjacency.containsKey(neighbor)) {
                    detectCycle(context, resourceType, neighbor, adjacency, typeLocations, visited, inStack, path)
                }
            } else if (neighbor in inStack) {
                // Found a cycle — report it
                val cycleStart = path.indexOf(neighbor)
                val cycle = if (cycleStart >= 0) {
                    path.subList(cycleStart, path.size) + neighbor
                } else {
                    listOf(node, neighbor)
                }
                val cycleStr = cycle.joinToString(" -> ")
                val location = typeLocations[node] ?: typeLocations[neighbor] ?: continue
                context.report(
                    ISSUE,
                    location,
                    "Cycle detected in $resourceType resource definitions: $cycleStr"
                )
                // Only report once per cycle entry point
                break
            }
        }

        path.removeLastOrNull()
        inStack.remove(node)
    }

    private fun ensureNode(resourceType: String, name: String) {
        graph.getOrPut(resourceType) { mutableMapOf() }.getOrPut(name) { mutableListOf() }
    }

    private fun addEdge(resourceType: String, from: String, to: String) {
        graph.getOrPut(resourceType) { mutableMapOf() }.getOrPut(from) { mutableListOf() }.add(to)
        // Ensure the target node exists in the graph too
        graph.getOrPut(resourceType) { mutableMapOf() }.getOrPut(to) { mutableListOf() }
    }

    /**
     * Strips the resource prefix (e.g. "@style/", "@color/", "@drawable/") from a reference string
     * and returns the name, or null if it doesn't match the expected type.
     */
    private fun stripResourcePrefix(value: String, resourceType: String): String? {
        val trimmed = value.trim()
        // Handle dot-notation style parent (no @ prefix)
        if (!trimmed.startsWith("@") && !trimmed.startsWith("?") && resourceType == "style") {
            return trimmed
        }
        val url = ResourceUrl.parse(trimmed) ?: return null
        if (url.type.getName() != resourceType) return null
        if (url.namespace != null && url.namespace != ResourceNamespace.RES_AUTO.packageName &&
            url.namespace != "") {
            // Skip references to external namespaces (e.g., android:)
            return null
        }
        return url.name
    }

    /**
     * Parses a resource reference like "@color/foo" and returns the resource name
     * if it matches the expected type.
     */
    private fun parseResourceReference(value: String, resourceType: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("@") && !trimmed.startsWith("?")) return null
        return stripResourcePrefix(trimmed, resourceType)
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions when the resources are resolved.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }
}
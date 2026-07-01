package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    // Maps from resource type -> (resource name -> set of referenced resource names of same type)
    private val graphs: MutableMap<ResourceType, MutableMap<String, MutableSet<String>>> = HashMap()

    // Store locations for reporting: (type, name) -> XmlContext + Element
    private data class ResourceKey(val type: ResourceType, val name: String)
    private val locations: MutableMap<ResourceKey, Pair<XmlContext, Element>> = HashMap()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        graphs.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
            folderType == ResourceFolderType.LAYOUT ||
            folderType == ResourceFolderType.DRAWABLE ||
            folderType == ResourceFolderType.COLOR ||
            folderType == ResourceFolderType.ANIM ||
            folderType == ResourceFolderType.ANIMATOR ||
            folderType == ResourceFolderType.XML ||
            folderType == ResourceFolderType.MENU
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_DRAWABLE,
            TAG_ITEM
        )
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(
            ATTR_STYLE,
            ATTR_LAYOUT,
            ATTR_DRAWABLE,
            ATTR_COLOR,
            ATTR_ID
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName ?: return

        when (tag) {
            TAG_STYLE -> {
                val name = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (name.isEmpty()) return
                val parent = element.getAttributeNS(null, "parent") ?: ""
                if (parent.isNotEmpty()) {
                    val parentName = stripStylePrefix(parent)
                    if (parentName.isNotEmpty()) {
                        addEdge(ResourceType.STYLE, name, parentName)
                        recordLocation(ResourceType.STYLE, name, context, element)
                    }
                } else {
                    // Implicit parent via dot notation: "ParentStyle.ChildStyle"
                    val dotIndex = name.lastIndexOf('.')
                    if (dotIndex > 0) {
                        val implicitParent = name.substring(0, dotIndex)
                        addEdge(ResourceType.STYLE, name, implicitParent)
                        recordLocation(ResourceType.STYLE, name, context, element)
                    }
                }
            }
            TAG_COLOR -> {
                val name = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (name.isEmpty()) return
                val text = element.textContent?.trim() ?: return
                val ref = parseResourceReference(text, ResourceType.COLOR)
                if (ref != null) {
                    addEdge(ResourceType.COLOR, name, ref)
                    recordLocation(ResourceType.COLOR, name, context, element)
                }
            }
            TAG_DRAWABLE -> {
                val name = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (name.isEmpty()) return
                val text = element.textContent?.trim() ?: return
                val ref = parseResourceReference(text, ResourceType.DRAWABLE)
                if (ref != null) {
                    addEdge(ResourceType.DRAWABLE, name, ref)
                    recordLocation(ResourceType.DRAWABLE, name, context, element)
                }
            }
            TAG_ITEM -> {
                val typeAttr = element.getAttributeNS(null, ATTR_TYPE) ?: ""
                val name = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (name.isEmpty()) return
                val text = element.textContent?.trim() ?: return
                if (text.isEmpty()) return

                val resourceType = when (typeAttr) {
                    "color" -> ResourceType.COLOR
                    "drawable" -> ResourceType.DRAWABLE
                    "layout" -> ResourceType.LAYOUT
                    "style" -> ResourceType.STYLE
                    "string" -> ResourceType.STRING
                    "dimen" -> ResourceType.DIMEN
                    "bool" -> ResourceType.BOOL
                    "integer" -> ResourceType.INTEGER
                    "array" -> ResourceType.ARRAY
                    else -> null
                } ?: return

                val ref = parseResourceReference(text, resourceType)
                if (ref != null) {
                    addEdge(resourceType, name, ref)
                    recordLocation(resourceType, name, context, element)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val localName = attribute.localName ?: attribute.name ?: return

        when (localName) {
            ATTR_STYLE -> {
                val ref = parseResourceReference(value, ResourceType.STYLE) ?: return
                // The element this attribute belongs to
                val ownerElement = attribute.ownerElement ?: return
                val ownerStyle = ownerElement.getAttributeNS(ANDROID_URI, ATTR_ID)
                    ?.removePrefix("@+id/")
                    ?.removePrefix("@id/")
                    ?: return
                if (ownerStyle.isNotEmpty()) {
                    addEdge(ResourceType.STYLE, ownerStyle, ref)
                    recordLocation(ResourceType.STYLE, ownerStyle, context, ownerElement)
                }
            }
            ATTR_LAYOUT -> {
                val ref = parseResourceReference(value, ResourceType.LAYOUT) ?: return
                val ownerElement = attribute.ownerElement ?: return
                val fileName = context.file.nameWithoutExtension
                if (fileName.isNotEmpty()) {
                    addEdge(ResourceType.LAYOUT, fileName, ref)
                    recordLocation(ResourceType.LAYOUT, fileName, context, ownerElement)
                }
            }
            ATTR_DRAWABLE -> {
                val ref = parseResourceReference(value, ResourceType.DRAWABLE) ?: return
                val ownerElement = attribute.ownerElement ?: return
                val fileName = context.file.nameWithoutExtension
                if (fileName.isNotEmpty()) {
                    addEdge(ResourceType.DRAWABLE, fileName, ref)
                    recordLocation(ResourceType.DRAWABLE, fileName, context, ownerElement)
                }
            }
            ATTR_COLOR -> {
                val ref = parseResourceReference(value, ResourceType.COLOR) ?: return
                val ownerElement = attribute.ownerElement ?: return
                val fileName = context.file.nameWithoutExtension
                if (fileName.isNotEmpty()) {
                    addEdge(ResourceType.COLOR, fileName, ref)
                    recordLocation(ResourceType.COLOR, fileName, context, ownerElement)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for ((type, graph) in graphs) {
            val cycles = findCycles(graph)
            for (cycle in cycles) {
                // Find the best location to report: use the first node in the cycle that has a location
                val reportNode = cycle.firstOrNull { locations.containsKey(ResourceKey(type, it)) }
                    ?: continue
                val (xmlContext, element) = locations[ResourceKey(type, reportNode)] ?: continue
                val cycleDescription = cycle.joinToString(" -> ") + " -> " + cycle.first()
                xmlContext.report(
                    ISSUE,
                    element,
                    xmlContext.getElementLocation(element),
                    "Cycle in resource definitions: `$cycleDescription`"
                )
            }
        }
    }

    // ---- Helpers ----

    private fun addEdge(type: ResourceType, from: String, to: String) {
        val graph = graphs.getOrPut(type) { HashMap() }
        graph.getOrPut(from) { HashSet() }.add(to)
    }

    private fun recordLocation(type: ResourceType, name: String, context: XmlContext, element: Element) {
        val key = ResourceKey(type, name)
        if (!locations.containsKey(key)) {
            locations[key] = Pair(context, element)
        }
    }

    private fun parseResourceReference(value: String, expectedType: ResourceType): String? {
        // Accepts @type/name or @+type/name
        val trimmed = value.trim()
        if (!trimmed.startsWith("@")) return null
        val withoutAt = trimmed.removePrefix("@").removePrefix("+")
        val slashIndex = withoutAt.indexOf('/') 
        if (slashIndex < 0) return null
        val typeStr = withoutAt.substring(0, slashIndex)
        val name = withoutAt.substring(slashIndex + 1)
        if (name.isEmpty()) return null
        // Accept matching type or no namespace prefix
        val bareType = typeStr.substringAfterLast(':')
        val matchesType = when (expectedType) {
            ResourceType.STYLE -> bareType == "style"
            ResourceType.COLOR -> bareType == "color"
            ResourceType.DRAWABLE -> bareType == "drawable"
            ResourceType.LAYOUT -> bareType == "layout"
            ResourceType.STRING -> bareType == "string"
            ResourceType.DIMEN -> bareType == "dimen"
            ResourceType.BOOL -> bareType == "bool"
            ResourceType.INTEGER -> bareType == "integer"
            ResourceType.ARRAY -> bareType == "array"
            else -> false
        }
        return if (matchesType) name else null
    }

    private fun stripStylePrefix(parent: String): String {
        // Remove @style/ or style/ prefix
        return when {
            parent.startsWith("@android:style/") -> parent.removePrefix("@android:style/")
            parent.startsWith("@style/") -> parent.removePrefix("@style/")
            parent.startsWith("style/") -> parent.removePrefix("style/")
            parent.startsWith("android:") -> "" // skip android namespace refs for cycle detection
            else -> parent
        }
    }

    /**
     * Finds all simple cycles in the directed graph using DFS.
     * Returns a list of cycles, each cycle is a list of node names.
     */
    private fun findCycles(graph: Map<String, Set<String>>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            if (node in inStack) {
                // Found a cycle; extract it from path
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    val cycle = path.subList(cycleStart, path.size).toList()
                    // Normalize cycle to avoid duplicates (start from lexicographically smallest)
                    val minIndex = cycle.indices.minByOrNull { cycle[it] } ?: 0
                    val normalized = cycle.subList(minIndex, cycle.size) + cycle.subList(0, minIndex)
                    if (normalized !in cycles) {
                        cycles.add(normalized)
                    }
                }
                return
            }
            if (node in visited) return
            visited.add(node)
            inStack.add(node)
            path.add(node)
            for (neighbor in graph[node] ?: emptySet()) {
                dfs(neighbor)
            }
            path.removeAt(path.size - 1)
            inStack.remove(node)
        }

        for (node in graph.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }
        return cycles
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions at the point where the resource is inflated or resolved.
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
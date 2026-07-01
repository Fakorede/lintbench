package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import java.util.EnumSet
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )

        private val RESOURCE_TYPE_NAMES = mapOf(
            ResourceType.STYLE to "style",
            ResourceType.COLOR to "color",
            ResourceType.DRAWABLE to "drawable",
            ResourceType.LAYOUT to "layout",
            ResourceType.DIMEN to "dimen",
            ResourceType.STRING to "string",
            ResourceType.INTEGER to "integer",
            ResourceType.BOOL to "bool",
            ResourceType.ARRAY to "array",
            ResourceType.ATTR to "attr",
            ResourceType.ANIM to "anim",
            ResourceType.ANIMATOR to "animator",
            ResourceType.INTERPOLATOR to "interpolator",
            ResourceType.MENU to "menu",
            ResourceType.MIPMAP to "mipmap",
            ResourceType.FONT to "font",
            ResourceType.FRACTION to "fraction",
            ResourceType.PLURALS to "plurals"
        )
    }

    // Graph: type -> (from -> list of to)
    private val graphs: MutableMap<ResourceType, MutableMap<String, MutableList<String>>> =
        mutableMapOf()

    // Location of definition: type -> name -> location
    private val defLocations: MutableMap<ResourceType, MutableMap<String, Location>> =
        mutableMapOf()

    // Location of reference: type -> from -> to -> location
    private val refLocations: MutableMap<ResourceType, MutableMap<String, MutableMap<String, Location>>> =
        mutableMapOf()

    // Track reported cycles to avoid duplicates
    private val reportedCycles = mutableSetOf<String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return

        when (folderType) {
            ResourceFolderType.VALUES -> handleValuesDocument(context, document)
            ResourceFolderType.DRAWABLE -> handleFileResource(context, document, ResourceType.DRAWABLE)
            ResourceFolderType.COLOR -> handleFileResource(context, document, ResourceType.COLOR)
            ResourceFolderType.LAYOUT -> handleLayoutResource(context, document)
            ResourceFolderType.ANIM -> handleFileResource(context, document, ResourceType.ANIM)
            ResourceFolderType.ANIMATOR -> handleFileResource(context, document, ResourceType.ANIMATOR)
            ResourceFolderType.INTERPOLATOR -> handleFileResource(context, document, ResourceType.INTERPOLATOR)
            ResourceFolderType.MENU -> handleFileResource(context, document, ResourceType.MENU)
            ResourceFolderType.MIPMAP -> handleFileResource(context, document, ResourceType.MIPMAP)
            ResourceFolderType.FONT -> handleFileResource(context, document, ResourceType.FONT)
            ResourceFolderType.XML -> handleFileResource(context, document, ResourceType.XML)
            else -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Values file handling
    // -------------------------------------------------------------------------

    private fun handleValuesDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                handleValuesElement(context, node)
            }
        }
    }

    private fun handleValuesElement(context: XmlContext, element: Element) {
        val tag = element.tagName
        when {
            tag == TAG_STYLE -> handleStyleElement(context, element)
            tag == TAG_ITEM -> {
                val typeAttr = element.getAttribute(ATTR_TYPE)
                if (typeAttr == "style") {
                    handleStyleElement(context, element)
                } else if (typeAttr.isNotEmpty()) {
                    val resType = ResourceType.fromXmlValue(typeAttr)
                    if (resType != null) {
                        handleSimpleValueElement(context, element, resType)
                    }
                }
            }
            else -> {
                // Try to map tag to resource type
                val resType = tagToResourceType(tag)
                if (resType != null) {
                    handleSimpleValueElement(context, element, resType)
                }
            }
        }
    }

    private fun tagToResourceType(tag: String): ResourceType? {
        return when (tag) {
            "color" -> ResourceType.COLOR
            "drawable" -> ResourceType.DRAWABLE
            "string" -> ResourceType.STRING
            "dimen" -> ResourceType.DIMEN
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "fraction" -> ResourceType.FRACTION
            "plurals" -> ResourceType.PLURALS
            "array", "string-array", "integer-array" -> ResourceType.ARRAY
            "attr" -> ResourceType.ATTR
            else -> null
        }
    }

    private fun handleStyleElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).trim().takeIf { it.isNotEmpty() } ?: return

        ensureNode(ResourceType.STYLE, name, context.getLocation(element))

        // Determine parent
        val parentAttr = element.getAttribute(ATTR_PARENT).trim()
        val resolvedParent: String? = when {
            parentAttr.isNotEmpty() -> parseStyleRef(parentAttr)
            name.contains('.') -> name.substringBeforeLast('.')
            else -> null
        }

        if (resolvedParent != null && resolvedParent.isNotEmpty()) {
            addEdge(ResourceType.STYLE, name, resolvedParent, context.getLocation(element))
        }
    }

    private fun handleSimpleValueElement(context: XmlContext, element: Element, type: ResourceType) {
        val name = element.getAttribute(ATTR_NAME).trim().takeIf { it.isNotEmpty() } ?: return

        ensureNode(type, name, context.getLocation(element))

        val text = element.textContent?.trim() ?: return
        if (text.isEmpty()) return

        val ref = extractSameTypeRef(text, type) ?: return
        addEdge(type, name, ref, context.getLocation(element))
    }

    // -------------------------------------------------------------------------
    // File-based resource handling
    // -------------------------------------------------------------------------

    private fun handleFileResource(context: XmlContext, document: Document, type: ResourceType) {
        val resourceName = context.file.nameWithoutExtension
        val root = document.documentElement ?: return
        ensureNode(type, resourceName, context.getLocation(root))
        scanElementForRefs(context, root, type, resourceName)
    }

    private fun scanElementForRefs(
        context: XmlContext,
        element: Element,
        type: ResourceType,
        resourceName: String
    ) {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? Attr ?: continue
            val value = attr.nodeValue?.trim() ?: continue
            val ref = extractSameTypeRef(value, type)
            if (ref != null) {
                addEdge(type, resourceName, ref, context.getLocation(attr))
            }
        }

        val children = element.childNodes
        var hasElementChildren = false
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                hasElementChildren = true
                break
            }
        }

        if (!hasElementChildren) {
            val text = element.textContent?.trim() ?: ""
            if (text.isNotEmpty()) {
                val ref = extractSameTypeRef(text, type)
                if (ref != null) {
                    addEdge(type, resourceName, ref, context.getLocation(element))
                }
            }
        }

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                scanElementForRefs(context, child, type, resourceName)
            }
        }
    }

    private fun handleLayoutResource(context: XmlContext, document: Document) {
        val resourceName = context.file.nameWithoutExtension
        val root = document.documentElement ?: return
        ensureNode(ResourceType.LAYOUT, resourceName, context.getLocation(root))
        scanLayoutElement(context, root, resourceName)
    }

    private fun scanLayoutElement(context: XmlContext, element: Element, resourceName: String) {
        if (element.tagName == "include") {
            val layout = element.getAttribute("layout").trim()
            if (layout.isNotEmpty()) {
                val ref = extractSameTypeRef(layout, ResourceType.LAYOUT)
                if (ref != null) {
                    addEdge(ResourceType.LAYOUT, resourceName, ref, context.getLocation(element))
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                scanLayoutElement(context, child, resourceName)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Graph helpers
    // -------------------------------------------------------------------------

    private fun ensureNode(type: ResourceType, name: String, location: Location) {
        graphs.getOrPut(type) { mutableMapOf() }.getOrPut(name) { mutableListOf() }
        val locMap = defLocations.getOrPut(type) { mutableMapOf() }
        if (!locMap.containsKey(name)) {
            locMap[name] = location
        }
    }

    private fun addEdge(type: ResourceType, from: String, to: String, location: Location) {
        val graph = graphs.getOrPut(type) { mutableMapOf() }
        graph.getOrPut(from) { mutableListOf() }.add(to)
        // Ensure 'to' exists as a node too
        graph.getOrPut(to) { mutableListOf() }

        val refLoc = refLocations.getOrPut(type) { mutableMapOf() }
        val nodeRefLoc = refLoc.getOrPut(from) { mutableMapOf() }
        if (!nodeRefLoc.containsKey(to)) {
            nodeRefLoc[to] = location
        }
    }

    // -------------------------------------------------------------------------
    // Cycle detection
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        for ((type, graph) in graphs) {
            val locMap = defLocations[type] ?: emptyMap()
            val refLocMap = refLocations[type] ?: emptyMap()
            detectCycles(context, type, graph, locMap, refLocMap)
        }
    }

    private fun detectCycles(
        context: Context,
        type: ResourceType,
        graph: Map<String, List<String>>,
        locMap: Map<String, Location>,
        refLocMap: Map<String, Map<String, Location>>
    ) {
        // Color: 0=unvisited, 1=in-stack, 2=done
        val color = mutableMapOf<String, Int>()

        fun dfs(node: String, path: MutableList<String>) {
            val c = color[node] ?: 0
            if (c == 2) return
            if (c == 1) {
                // Found a cycle - find where it starts in path
                val cycleStart = path.indexOf(node)
                val cycle = if (cycleStart >= 0) {
                    path.subList(cycleStart, path.size).toList()
                } else {
                    path.toList()
                }

                // Create a canonical key for this cycle to avoid duplicates
                val cycleKey = "${type.getName()}:" + cycle.sorted().joinToString(",")
                if (reportedCycles.add(cycleKey)) {
                    val prevNode = path.lastOrNull()

                    val location: Location? = if (prevNode != null) {
                        refLocMap[prevNode]?.get(node)
                            ?: locMap[prevNode]
                            ?: locMap[node]
                    } else {
                        locMap[node]
                    }

                    val typeName = type.getName()
                    val message = if (cycle.size == 1 && cycle[0] == node) {
                        // Self-reference
                        "${typeName.replaceFirstChar { it.uppercase() }} `$node` should not reference itself"
                    } else {
                        val cycleDesc = (cycle + node).joinToString(" -> ")
                        "Cycle detected in $typeName resource definitions: $cycleDesc"
                    }

                    if (location != null) {
                        context.report(ISSUE, location, message)
                    }
                }
                return
            }

            color[node] = 1
            path.add(node)
            for (neighbor in graph[node] ?: emptyList()) {
                dfs(neighbor, path)
            }
            path.removeAt(path.size - 1)
            color[node] = 2
        }

        for (node in graph.keys) {
            if ((color[node] ?: 0) == 0) {
                dfs(node, mutableListOf())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reference parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Parse a style parent reference and return the plain style name,
     * or null if it's a framework reference or unrecognized.
     */
    private fun parseStyleRef(ref: String): String? {
        val trimmed = ref.trim()
        if (isFrameworkRef(trimmed)) return null

        // Strip common prefixes
        val prefixes = listOf("@style/", "?style/", "@attr/", "?attr/", "@*style/")
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix).trim()
            }
        }

        // If it doesn't start with @ or ?, treat as plain name
        if (!trimmed.startsWith("@") && !trimmed.startsWith("?")) {
            return trimmed
        }

        return null
    }

    /**
     * Extract a reference to the same resource type from a value string.
     */
    private fun extractSameTypeRef(text: String, type: ResourceType): String? {
        val trimmed = text.trim()
        if (isFrameworkRef(trimmed)) return null

        val typeName = RESOURCE_TYPE_NAMES[type] ?: type.getName()

        val prefixes = listOf(
            "@+$typeName/",
            "@$typeName/",
            "?$typeName/",
            "@*$typeName/"
        )

        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix).trim()
            }
        }

        return null
    }

    private fun isFrameworkRef(ref: String): Boolean {
        return ref.startsWith("@android:") ||
            ref.startsWith("?android:") ||
            ref.startsWith("@*android:") ||
            ref.startsWith("@android.") ||
            ref.startsWith("?android.")
    }
}
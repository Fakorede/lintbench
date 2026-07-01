package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
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
    }

    /**
     * Map from resource type to a map of resource name -> list of names it references.
     */
    private val graphs: MutableMap<ResourceType, MutableMap<String, MutableList<String>>> =
        mutableMapOf()

    /**
     * Map from resource type+name to the location where it was defined (for error reporting).
     */
    private val locations: MutableMap<ResourceType, MutableMap<String, Location>> = mutableMapOf()

    /**
     * Map from resource type -> from -> to -> location of the reference.
     */
    private val refLocations: MutableMap<ResourceType, MutableMap<String, MutableMap<String, Location>>> =
        mutableMapOf()

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return

        when (folderType) {
            ResourceFolderType.VALUES -> handleValuesDocument(context, document)
            ResourceFolderType.DRAWABLE -> handleFileDocument(context, document, ResourceType.DRAWABLE)
            ResourceFolderType.COLOR -> handleFileDocument(context, document, ResourceType.COLOR)
            ResourceFolderType.FONT -> handleFileDocument(context, document, ResourceType.FONT)
            ResourceFolderType.LAYOUT -> handleLayoutDocument(context, document)
            ResourceFolderType.MIPMAP -> handleFileDocument(context, document, ResourceType.MIPMAP)
            ResourceFolderType.ANIM -> handleFileDocument(context, document, ResourceType.ANIM)
            ResourceFolderType.ANIMATOR -> handleFileDocument(context, document, ResourceType.ANIMATOR)
            ResourceFolderType.INTERPOLATOR -> handleFileDocument(context, document, ResourceType.INTERPOLATOR)
            ResourceFolderType.MENU -> handleFileDocument(context, document, ResourceType.MENU)
            ResourceFolderType.XML -> handleFileDocument(context, document, ResourceType.XML)
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
        when (element.tagName) {
            TAG_STYLE -> handleStyle(context, element)
            TAG_COLOR -> handleSimpleValueRef(context, element, ResourceType.COLOR)
            TAG_DRAWABLE -> handleSimpleValueRef(context, element, ResourceType.DRAWABLE)
            TAG_ITEM -> handleItem(context, element)
            "string" -> handleSimpleValueRef(context, element, ResourceType.STRING)
            "dimen" -> handleSimpleValueRef(context, element, ResourceType.DIMEN)
            "integer" -> handleSimpleValueRef(context, element, ResourceType.INTEGER)
            "bool" -> handleSimpleValueRef(context, element, ResourceType.BOOL)
            "fraction" -> handleSimpleValueRef(context, element, ResourceType.FRACTION)
            "plurals" -> handleSimpleValueRef(context, element, ResourceType.PLURALS)
            "array" -> handleSimpleValueRef(context, element, ResourceType.ARRAY)
            "string-array" -> handleSimpleValueRef(context, element, ResourceType.ARRAY)
            "integer-array" -> handleSimpleValueRef(context, element, ResourceType.ARRAY)
            else -> {}
        }
    }

    private fun handleStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        // Ensure the node exists in the graph
        val graph = graphs.getOrPut(ResourceType.STYLE) { mutableMapOf() }
        graph.getOrPut(name) { mutableListOf() }

        val loc = locations.getOrPut(ResourceType.STYLE) { mutableMapOf() }
        if (!loc.containsKey(name)) {
            loc[name] = context.getLocation(element)
        }

        // Determine parent
        val parentAttr = element.getAttribute(ATTR_PARENT)
        val resolvedParent: String? = when {
            parentAttr.isNotEmpty() -> stripStyleRef(parentAttr)
            name.contains('.') -> name.substringBeforeLast('.')
            else -> null
        }

        if (resolvedParent != null && resolvedParent.isNotEmpty()) {
            graph.getOrPut(name) { mutableListOf() }.add(resolvedParent)
            val refLoc = refLocations.getOrPut(ResourceType.STYLE) { mutableMapOf() }
            val nodeRefLoc = refLoc.getOrPut(name) { mutableMapOf() }
            if (!nodeRefLoc.containsKey(resolvedParent)) {
                nodeRefLoc[resolvedParent] = context.getLocation(element)
            }
        }
    }

    private fun handleSimpleValueRef(context: XmlContext, element: Element, type: ResourceType) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        // Ensure node exists
        val graph = graphs.getOrPut(type) { mutableMapOf() }
        graph.getOrPut(name) { mutableListOf() }
        val loc = locations.getOrPut(type) { mutableMapOf() }
        if (!loc.containsKey(name)) {
            loc[name] = context.getLocation(element)
        }

        val text = element.textContent?.trim() ?: return
        if (text.isEmpty()) return
        val ref = extractRef(text, type) ?: return
        addEdge(context, type, name, ref, null, element)
    }

    private fun handleItem(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val typeAttr = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val resourceType = ResourceType.fromXmlValue(typeAttr) ?: return

        if (resourceType == ResourceType.STYLE) {
            handleStyle(context, element)
            return
        }

        // Ensure node exists
        val graph = graphs.getOrPut(resourceType) { mutableMapOf() }
        graph.getOrPut(name) { mutableListOf() }
        val loc = locations.getOrPut(resourceType) { mutableMapOf() }
        if (!loc.containsKey(name)) {
            loc[name] = context.getLocation(element)
        }

        val text = element.textContent?.trim() ?: return
        if (text.isEmpty()) return
        val ref = extractRef(text, resourceType) ?: return
        addEdge(context, resourceType, name, ref, null, element)
    }

    // -------------------------------------------------------------------------
    // File-based resource handling
    // -------------------------------------------------------------------------

    private fun handleFileDocument(context: XmlContext, document: Document, type: ResourceType) {
        val resourceName = context.file.nameWithoutExtension
        val root = document.documentElement ?: return
        scanElementForRefs(context, root, type, resourceName)
    }

    private fun scanElementForRefs(
        context: XmlContext,
        element: Element,
        type: ResourceType,
        resourceName: String
    ) {
        // Check all attributes for references
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? org.w3c.dom.Attr ?: continue
            val value = attr.nodeValue ?: continue
            val ref = extractRef(value, type)
            if (ref != null) {
                addEdge(context, type, resourceName, ref, attr, element)
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
                val ref = extractRef(text, type)
                if (ref != null) {
                    addEdge(context, type, resourceName, ref, null, element)
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

    private fun handleLayoutDocument(context: XmlContext, document: Document) {
        val resourceName = context.file.nameWithoutExtension
        scanLayoutElement(context, document.documentElement ?: return, resourceName)
    }

    private fun scanLayoutElement(context: XmlContext, element: Element, resourceName: String) {
        if (element.tagName == "include") {
            val layout = element.getAttribute("layout").takeIf { it.isNotEmpty() }
            if (layout != null) {
                val ref = extractRef(layout, ResourceType.LAYOUT)
                if (ref != null) {
                    addEdge(context, ResourceType.LAYOUT, resourceName, ref, null, element)
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
    // Edge management
    // -------------------------------------------------------------------------

    private fun addEdge(
        context: XmlContext,
        type: ResourceType,
        from: String,
        to: String,
        attr: org.w3c.dom.Attr?,
        element: Element
    ) {
        val graph = graphs.getOrPut(type) { mutableMapOf() }
        val loc = locations.getOrPut(type) { mutableMapOf() }
        graph.getOrPut(from) { mutableListOf() }.add(to)
        if (!loc.containsKey(from)) {
            loc[from] = context.getLocation(element)
        }
        val refLoc = refLocations.getOrPut(type) { mutableMapOf() }
        val nodeRefLoc = refLoc.getOrPut(from) { mutableMapOf() }
        if (!nodeRefLoc.containsKey(to)) {
            nodeRefLoc[to] = if (attr != null) {
                context.getLocation(attr)
            } else {
                context.getLocation(element)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cycle detection after all files have been processed
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        for ((type, graph) in graphs) {
            val locMap = locations[type] ?: continue
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
        // 0=WHITE, 1=GRAY, 2=BLACK
        val color = mutableMapOf<String, Int>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>) {
            val c = color[node] ?: 0
            if (c == 2) return
            if (c == 1) {
                // Found a cycle
                val cycleStart = path.indexOf(node)
                val cycle = if (cycleStart >= 0) path.subList(cycleStart, path.size).toList() else path.toList()
                val cycleKey = cycle.sorted().joinToString(",")
                if (reported.add(cycleKey)) {
                    val cycleDesc = (cycle + node).joinToString(" -> ")
                    val prevNode = path.lastOrNull()
                    val location = if (prevNode != null) {
                        refLocMap[prevNode]?.get(node) ?: locMap[node]
                    } else {
                        locMap[node]
                    }

                    val typeName = type.getName()
                    val message = if (cycle.size == 1 && cycle[0] == node) {
                        "${typeName.replaceFirstChar { it.uppercase() }} `${node}` should not reference itself"
                    } else {
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
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Strip a style reference to get the plain style name.
     * Returns null if it's a framework/android reference (we don't track those).
     */
    private fun stripStyleRef(ref: String): String? {
        val trimmed = ref.trim()
        // Skip android framework references
        if (trimmed.startsWith("@android:") || trimmed.startsWith("?android:")) return null
        if (trimmed.startsWith("@android.") || trimmed.startsWith("?android.")) return null

        val prefixes = listOf("@style/", "?style/", "@attr/", "?attr/", "?/")
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix)
            }
        }
        // Plain name (no @ or ?)
        return if (!trimmed.startsWith("@") && !trimmed.startsWith("?")) trimmed else null
    }

    /**
     * Extract a same-type resource reference from a value string.
     * Returns the referenced resource name, or null if not a reference to the given type.
     */
    private fun extractRef(text: String, type: ResourceType): String? {
        val trimmed = text.trim()
        // Skip android framework references
        if (trimmed.startsWith("@android:") || trimmed.startsWith("?android:")) return null
        if (trimmed.startsWith("@*android:") || trimmed.startsWith("@+android:")) return null

        val typeName = type.getName()

        // @+type/name
        val atPlusPrefix = "@+$typeName/"
        if (trimmed.startsWith(atPlusPrefix)) {
            return trimmed.removePrefix(atPlusPrefix)
        }

        // @type/name
        val atPrefix = "@$typeName/"
        if (trimmed.startsWith(atPrefix)) {
            return trimmed.removePrefix(atPrefix)
        }

        // ?type/name
        val qPrefix = "?$typeName/"
        if (trimmed.startsWith(qPrefix)) {
            return trimmed.removePrefix(qPrefix)
        }

        // @*type/name (framework private, but non-android)
        val starPrefix = "@*$typeName/"
        if (trimmed.startsWith(starPrefix)) {
            return trimmed.removePrefix(starPrefix)
        }

        return null
    }
}
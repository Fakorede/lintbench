package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

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
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )

        private const val ATTR_REF = "ref"
    }

    /**
     * Map from resource type to a map of resource name -> list of names it references.
     * e.g. styleGraph["style"]["MyStyle"] = ["ParentStyle", ...]
     */
    private val graphs: MutableMap<ResourceType, MutableMap<String, MutableList<String>>> =
        mutableMapOf()

    /**
     * Map from resource type+name to the location where it was defined (for error reporting).
     */
    private val locations: MutableMap<ResourceType, MutableMap<String, Location>> = mutableMapOf()

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_STYLE, TAG_COLOR, TAG_DRAWABLE, TAG_ITEM)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> handleStyle(context, element)
            TAG_COLOR -> handleColorOrDrawable(context, element, ResourceType.COLOR)
            TAG_DRAWABLE -> handleColorOrDrawable(context, element, ResourceType.DRAWABLE)
            TAG_ITEM -> handleItem(context, element)
        }
    }

    private fun handleStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = element.getAttribute(ATTR_PARENT)

        // Resolve implicit parent via dot notation (e.g. "MyTheme.Child" -> parent "MyTheme")
        val resolvedParent: String? = when {
            parent.isNotEmpty() -> {
                // Strip @style/ prefix if present
                stripStyleRef(parent)
            }
            name.contains('.') -> {
                // Implicit parent: everything before the last dot
                name.substringBeforeLast('.')
            }
            else -> null
        }

        val graph = graphs.getOrPut(ResourceType.STYLE) { mutableMapOf() }
        val loc = locations.getOrPut(ResourceType.STYLE) { mutableMapOf() }

        val deps = graph.getOrPut(name) { mutableListOf() }
        if (resolvedParent != null && resolvedParent.isNotEmpty()) {
            deps.add(resolvedParent)
        }
        if (!loc.containsKey(name)) {
            loc[name] = context.getLocation(element)
        }
    }

    private fun handleColorOrDrawable(context: XmlContext, element: Element, type: ResourceType) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        // Check if the text content is a reference to another resource of the same type
        val text = element.textContent?.trim() ?: return
        val ref = extractSameTypeRef(text, type) ?: return

        val graph = graphs.getOrPut(type) { mutableMapOf() }
        val loc = locations.getOrPut(type) { mutableMapOf() }

        graph.getOrPut(name) { mutableListOf() }.add(ref)
        if (!loc.containsKey(name)) {
            loc[name] = context.getLocation(element)
        }
    }

    private fun handleItem(context: XmlContext, element: Element) {
        // <item> inside a <resources> block can define colors, drawables, styles, etc.
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val typeAttr = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val resourceType = ResourceType.fromXmlValue(typeAttr) ?: return

        // For style items, check parent attribute
        if (resourceType == ResourceType.STYLE) {
            val parent = element.getAttribute(ATTR_PARENT)
            val resolvedParent = if (parent.isNotEmpty()) stripStyleRef(parent) else null
            val graph = graphs.getOrPut(ResourceType.STYLE) { mutableMapOf() }
            val loc = locations.getOrPut(ResourceType.STYLE) { mutableMapOf() }
            val deps = graph.getOrPut(name) { mutableListOf() }
            if (resolvedParent != null && resolvedParent.isNotEmpty()) {
                deps.add(resolvedParent)
            }
            if (!loc.containsKey(name)) {
                loc[name] = context.getLocation(element)
            }
            return
        }

        // For color/drawable items, check text content
        val text = element.textContent?.trim() ?: return
        val ref = extractSameTypeRef(text, resourceType) ?: return

        val graph = graphs.getOrPut(resourceType) { mutableMapOf() }
        val loc = locations.getOrPut(resourceType) { mutableMapOf() }
        graph.getOrPut(name) { mutableListOf() }.add(ref)
        if (!loc.containsKey(name)) {
            loc[name] = context.getLocation(element)
        }
    }

    // -------------------------------------------------------------------------
    // Cycle detection after all files have been processed
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        for ((type, graph) in graphs) {
            val locMap = locations[type] ?: continue
            detectCycles(context, type, graph, locMap)
        }
    }

    private fun detectCycles(
        context: Context,
        type: ResourceType,
        graph: Map<String, List<String>>,
        locMap: Map<String, Location>
    ) {
        // Standard DFS cycle detection with coloring: WHITE=0, GRAY=1, BLACK=2
        val color = mutableMapOf<String, Int>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>): Boolean {
            val c = color[node] ?: 0
            if (c == 2) return false // already fully processed
            if (c == 1) {
                // Found a cycle — report it
                val cycleStart = path.indexOf(node)
                val cycle = if (cycleStart >= 0) path.subList(cycleStart, path.size) else path
                val cycleKey = cycle.sorted().joinToString(",")
                if (reported.add(cycleKey)) {
                    val cycleDesc = (cycle + node).joinToString(" -> ")
                    val location = locMap[node] ?: locMap[cycle.firstOrNull() ?: node]
                    if (location != null) {
                        context.report(
                            ISSUE,
                            location,
                            "Cycle detected in ${type.getName()} resource definitions: $cycleDesc"
                        )
                    }
                }
                return true
            }

            color[node] = 1
            path.add(node)
            for (neighbor in graph[node] ?: emptyList()) {
                dfs(neighbor, path)
            }
            path.removeAt(path.size - 1)
            color[node] = 2
            return false
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
     * Strips a style reference prefix like "@style/" or "?style/" and returns the bare name.
     * Returns null if the reference points to a framework resource (android:).
     */
    private fun stripStyleRef(ref: String): String? {
        val trimmed = ref.trim()
        if (trimmed.startsWith("@android:") || trimmed.startsWith("?android:")) return null
        val prefixes = listOf("@style/", "?style/", "@attr/", "?attr/")
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix)
            }
        }
        // Could be a bare name
        return if (!trimmed.startsWith("@") && !trimmed.startsWith("?")) trimmed else null
    }

    /**
     * Given a text value like "@color/foo" or "@drawable/bar", returns the referenced name
     * if it matches [type], otherwise null.
     */
    private fun extractSameTypeRef(text: String, type: ResourceType): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("@android:") || trimmed.startsWith("?android:")) return null
        val prefix = "@${type.getName()}/"
        return if (trimmed.startsWith(prefix)) {
            trimmed.removePrefix(prefix)
        } else null
    }
}
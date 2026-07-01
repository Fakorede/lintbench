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
     * e.g. styleGraph["style"]["MyStyle"] = ["MyStyle.Parent", ...]
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
                stripResourcePrefix(parent, ResourceType.STYLE)
            }
            name.contains('.') -> {
                // Implicit parent: everything before the last dot
                name.substringBeforeLast('.')
            }
            else -> null
        }

        recordResource(ResourceType.STYLE, name, resolvedParent, context.getLocation(element))
    }

    private fun handleColorOrDrawable(context: XmlContext, element: Element, type: ResourceType) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        // Check if the element's text content is a reference to another resource of the same type
        val text = element.textContent?.trim() ?: ""
        val ref = stripResourcePrefix(text, type)
        recordResource(type, name, ref, context.getLocation(element))
    }

    private fun handleItem(context: XmlContext, element: Element) {
        val typeName = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val type = ResourceType.fromXmlValue(typeName) ?: return
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        // The item may reference another resource via text content
        val text = element.textContent?.trim() ?: ""
        val ref = stripResourcePrefix(text, type)
        recordResource(type, name, ref, context.getLocation(element))
    }

    /**
     * Strip a resource reference prefix like "@style/", "@color/", "@drawable/" etc.
     * Returns null if the text is not a reference to the given type.
     */
    private fun stripResourcePrefix(text: String, type: ResourceType): String? {
        if (text.isBlank()) return null
        if (!text.startsWith("@")) return null
        val withoutAt = text.removePrefix("@")
        val prefix = "${type.getName()}/"
        return if (withoutAt.startsWith(prefix)) {
            withoutAt.removePrefix(prefix).trim()
        } else {
            null
        }
    }

    private fun recordResource(
        type: ResourceType,
        name: String,
        reference: String?,
        location: Location
    ) {
        val graph = graphs.getOrPut(type) { mutableMapOf() }
        val refs = graph.getOrPut(name) { mutableListOf() }
        if (reference != null && reference.isNotEmpty()) {
            refs.add(reference)
        }

        val locs = locations.getOrPut(type) { mutableMapOf() }
        if (!locs.containsKey(name)) {
            locs[name] = location
        }
    }

    // -------------------------------------------------------------------------
    // After all files have been processed, detect cycles
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        for ((type, graph) in graphs) {
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()

            for (node in graph.keys) {
                if (node !in visited) {
                    detectCycle(context, type, graph, node, visited, inStack, mutableListOf())
                }
            }
        }
    }

    private fun detectCycle(
        context: Context,
        type: ResourceType,
        graph: Map<String, List<String>>,
        node: String,
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
                detectCycle(context, type, graph, neighbor, visited, inStack, path)
            } else if (neighbor in inStack) {
                // Found a cycle — report it
                reportCycle(context, type, path, neighbor)
            }
        }

        path.removeAt(path.size - 1)
        inStack.remove(node)
    }

    private fun reportCycle(
        context: Context,
        type: ResourceType,
        path: List<String>,
        cycleStart: String
    ) {
        // Build the cycle description
        val cycleIndex = path.indexOf(cycleStart)
        val cyclePath = if (cycleIndex >= 0) {
            path.subList(cycleIndex, path.size) + cycleStart
        } else {
            path + cycleStart
        }

        val typeName = type.getName()
        val cycleDescription = cyclePath.joinToString(" => ") { "@$typeName/$it" }

        // Find the best location to report on
        val locs = locations[type]
        val location = locs?.get(cycleStart) ?: locs?.values?.firstOrNull()

        val message = "Cycle detected: $cycleDescription"

        if (location != null) {
            context.report(ISSUE, location, message)
        }
    }
}
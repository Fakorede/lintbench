package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node as DomNode
import java.util.LinkedHashSet

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<ResourceNode, MutableSet<ResourceNode>>()
    private val resourceLocations = mutableMapOf<ResourceNode, Pair<XmlContext, Element>>()

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        resourceLocations.clear()
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val current = currentResource(context, element) ?: return
        resourceLocations.putIfAbsent(current, context to element)

        val refs = mutableSetOf<ResourceNode>()

        // Style parent references.
        if (context.resourceFolderType == ResourceFolderType.VALUES && element.tagName == SdkConstants.TAG_STYLE) {
            val parentAttr = element.getAttributeNode(SdkConstants.ATTR_PARENT)
            if (parentAttr != null) {
                parseReference(parentAttr.value, "style")?.let { refs.add(it) }
            } else {
                val nameAttr = element.getAttributeNode(SdkConstants.ATTR_NAME)
                if (nameAttr != null) {
                    val name = nameAttr.value
                    val dot = name.lastIndexOf('.')
                    if (dot > 0) {
                        refs.add(ResourceNode("style", name.substring(0, dot)))
                    }
                }
            }
        }

        // Layout includes.
        if (context.resourceFolderType == ResourceFolderType.LAYOUT && element.tagName == SdkConstants.TAG_INCLUDE) {
            element.getAttributeNode(SdkConstants.ATTR_LAYOUT)?.value?.let { value ->
                parseReference(value, "layout")?.let { refs.add(it) }
            }
        }

        // Generic resource references in attributes.
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as Attr
            if (attr.prefix == "tools" || attr.name.startsWith("xmlns")) continue
            parseReference(attr.value, null)?.let { refs.add(it) }
        }

        // Resource references in element text (e.g. simple string values).
        parseReference(element.textContent, null)?.let { refs.add(it) }

        if (refs.isNotEmpty()) {
            graph.getOrPut(current) { mutableSetOf() }.addAll(refs)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        findCycles()
    }

    private fun currentResource(context: XmlContext, element: Element): ResourceNode? {
        val folderType = context.resourceFolderType ?: return null
        if (folderType != ResourceFolderType.VALUES) {
            return ResourceNode(folderType.getName(), context.file.nameWithoutExtension)
        }

        var node: DomNode? = element
        while (node != null && node.nodeType == DomNode.ELEMENT_NODE) {
            val el = node as Element
            if (el.parentNode?.nodeName == SdkConstants.TAG_RESOURCES) {
                val name = el.getAttribute(SdkConstants.ATTR_NAME)
                if (name.isNotBlank()) {
                    return ResourceNode(el.tagName, name)
                }
            }
            node = el.parentNode
        }
        return null
    }

    private fun parseReference(value: String, defaultType: String?): ResourceNode? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("@")) return null
        val match = REF_PATTERN.matchEntire(trimmed) ?: return null
        val packageName = match.groupValues[1]
        if (packageName.isNotEmpty()) return null
        val typeName = match.groupValues[2].takeIf { it.isNotEmpty() } ?: defaultType ?: return null
        if (typeName == "id") return null
        val name = match.groupValues[3]
        if (name.isEmpty()) return null
        return ResourceNode(typeName, name)
    }

    private fun findCycles() {
        val visited = mutableSetOf<ResourceNode>()
        val stack = LinkedHashSet<ResourceNode>()
        val reported = mutableSetOf<String>()

        for (node in graph.keys) {
            if (node !in visited) {
                dfs(node, visited, stack, reported)
            }
        }
    }

    private fun dfs(
        node: ResourceNode,
        visited: MutableSet<ResourceNode>,
        stack: LinkedHashSet<ResourceNode>,
        reported: MutableSet<String>
    ) {
        visited += node
        stack += node

        for (next in graph[node].orEmpty()) {
            if (next in stack) {
                val cycle = stack.dropWhile { it != next } + next
                val key = cycle.map { "${it.type}/${it.name}" }.sorted().joinToString(",")
                if (reported.add(key)) {
                    reportCycle(cycle)
                }
            } else if (next !in visited) {
                dfs(next, visited, stack, reported)
            }
        }

        stack -= node
    }

    private fun reportCycle(cycle: List<ResourceNode>) {
        val message = "Cycle in resource definitions: " + cycle.joinToString(" -> ") { "${it.type}/${it.name}" }
        for (node in cycle) {
            val (xmlContext, element) = resourceLocations[node] ?: continue
            xmlContext.report(ISSUE, xmlContext.getLocation(element), message)
        }
    }

    private data class ResourceNode(val type: String, val name: String)

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val REF_PATTERN = Regex("""^@(?:([a-zA-Z0-9_.]+):)?([a-zA-Z]+)/([a-zA-Z0-9_.]+)$""")
    }
}
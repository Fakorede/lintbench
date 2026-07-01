package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
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
import org.w3c.dom.Node
import java.util.EnumSet

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
                parseStyleParent(parentAttr.value)?.let { refs.add(it) }
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
                parseReference(value)?.let { refs.add(it) }
            }
        }

        // Generic resource references in attributes.
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as Attr
            if (attr.prefix == "tools" || attr.name.startsWith("xmlns")) continue
            parseReference(attr.value)?.let { refs.add(it) }
        }

        // Resource references in element text.
        parseReference(element.textContent)?.let { refs.add(it) }

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

        var node: Node? = element
        while (node != null && node.nodeType == Node.ELEMENT_NODE) {
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

    private fun parseReference(value: String): ResourceNode? {
        val url = ResourceUrl.parse(value.trim()) ?: return null
        if (url.theme) return null
        if (!url.packageName.isNullOrEmpty()) return null
        if (url.type == "id" && url.create) return null
        return ResourceNode(url.type, url.name)
    }

    private fun parseStyleParent(value: String): ResourceNode? {
        val trimmed = value.trim()
        val url = ResourceUrl.parse(trimmed)
        if (url != null) {
            if (url.theme) return null
            if (!url.packageName.isNullOrEmpty()) return null
            if (url.type != "style") return null
            return ResourceNode("style", url.name)
        }
        // Bare style name like "Parent" or "ParentStyle".
        if (trimmed.isNotEmpty() && trimmed.matches(Regex("""[a-zA-Z0-9_.]+"""))) {
            return ResourceNode("style", trimmed)
        }
        return null
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
                EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
        )
    }
}
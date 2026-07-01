package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
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

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    private var currentPackage: String = ""
    private val declarations = mutableMapOf<Node, Location>()
    private val edges = mutableMapOf<Node, MutableList<Edge>>()
    private val finished = mutableSetOf<Node>()
    private val reportedCycles = mutableSetOf<Set<Node>>()

    private data class Node(val type: String, val name: String)
    private data class Edge(val target: Node, val location: Location)

    override fun beforeCheckRootProject(context: Context) {
        currentPackage = context.project.getPackage() ?: ""
        declarations.clear()
        edges.clear()
        finished.clear()
        reportedCycles.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val node = getCurrentNode(context, element) ?: return
        declarations[node] = context.getLocation(element)

        val text = element.textContent
        if (text.isNotBlank()) {
            val location = context.getLocation(element)
            for (target in parseReferences(text)) {
                recordReference(node, target, location)
            }
        }

        if (element.tagName == "style") {
            val parent = element.getAttribute("parent")
            if (parent.isNotEmpty()) {
                recordStyleReference(node, parent, context.getLocation(element))
            } else {
                val name = element.getAttribute("name")
                if (name.contains('.')) {
                    val parentName = name.substringBeforeLast('.')
                    recordReference(node, Node("style", parentName), context.getLocation(element))
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        val from = getEnclosingResourceNode(context, element) ?: return
        val value = attribute.value ?: return

        if (element.tagName == "style" && attribute.name == "parent") {
            recordStyleReference(from, value, context.getLocation(attribute))
            return
        }

        val location = context.getLocation(attribute)
        for (target in parseReferences(value)) {
            recordReference(from, target, location)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        detectCycles(context)
    }

    private fun getEnclosingResourceNode(context: XmlContext, element: Element): Node? {
        var current: Element? = element
        while (current != null) {
            getCurrentNode(context, current)?.let { return it }
            val parent = current.parentNode
            current = if (parent is Element) parent else null
        }
        return null
    }

    private fun getCurrentNode(context: XmlContext, element: Element): Node? {
        val folderType = context.resourceFolderType ?: return null
        return if (folderType == ResourceFolderType.VALUES) {
            getValueResourceNode(element)
        } else {
            val type = folderType.name.lowercase()
            val name = context.file.nameWithoutExtension
            Node(type, name)
        }
    }

    private fun getValueResourceNode(element: Element): Node? {
        if (!element.hasAttribute("name")) return null
        val tag = element.tagName
        val type = if (tag == "item" && element.hasAttribute("type")) {
            element.getAttribute("type")
        } else {
            tag
        }
        return Node(type.lowercase(), element.getAttribute("name"))
    }

    private fun recordReference(from: Node?, target: Node?, location: Location) {
        if (from == null || target == null) return
        edges.getOrPut(from) { mutableListOf() }.add(Edge(target, location))
    }

    private fun recordStyleReference(from: Node, value: String, location: Location) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("@") || trimmed.startsWith("?")) {
            for (target in parseReferences(trimmed)) {
                recordReference(from, target, location)
            }
        } else {
            recordReference(from, Node("style", trimmed), location)
        }
    }

    private fun parseReferences(value: String): List<Node> {
        val result = mutableListOf<Node>()
        for (match in RESOURCE_REF_PATTERN.findAll(value)) {
            val packageName = match.groupValues[1]
            var type = match.groupValues[2]
            val name = match.groupValues[3]
            if (type.isEmpty()) {
                if (value[match.range.first] == '?') {
                    type = "attr"
                } else {
                    continue
                }
            }
            if (packageName.isNotEmpty() && packageName != currentPackage) {
                continue
            }
            result.add(Node(type.lowercase(), name))
        }
        return result
    }

    private fun detectCycles(context: Context) {
        finished.clear()
        for (node in declarations.keys) {
            if (node in finished) continue
            val stack = mutableListOf<Node>()
            val index = mutableMapOf<Node, Int>()
            dfs(context, node, stack, index)
        }
    }

    private fun dfs(context: Context, node: Node, stack: MutableList<Node>, index: MutableMap<Node, Int>) {
        if (node in finished) return
        if (node in index) {
            val cycle = stack.subList(index[node]!!, stack.size) + node
            reportCycle(context, cycle)
            return
        }
        index[node] = stack.size
        stack.add(node)
        for (edge in edges[node] ?: emptyList()) {
            if (edge.target in declarations) {
                dfs(context, edge.target, stack, index)
            }
        }
        stack.removeAt(stack.size - 1)
        index.remove(node)
        finished.add(node)
    }

    private fun reportCycle(context: Context, cycle: List<Node>) {
        val cycleSet = cycle.toSet()
        if (cycleSet in reportedCycles) return
        reportedCycles.add(cycleSet)

        val first = cycle.first()
        val last = cycle[cycle.size - 2]
        val location = edges[last]?.firstOrNull { it.target == first }?.location
            ?: declarations[first]
            ?: return

        val message = buildString {
            append("Cycle in resource definitions: ")
            cycle.joinTo(this, " -> ") { "${it.type}/${it.name}" }
        }
        context.report(ISSUE, location, message)
    }
}

private val RESOURCE_REF_PATTERN = Regex(
    "[@?]" +
    "(?:([a-zA-Z_][a-zA-Z0-9_.]*):)?" +
    "(?:([a-zA-Z_][a-zA-Z0-9_.]*)/)?" +
    "([a-zA-Z_][a-zA-Z0-9_.]*)"
)
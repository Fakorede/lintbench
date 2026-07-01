package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.regex.Pattern

class ResourceCycleDetector : ResourceXmlDetector() {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, com.android.tools.lint.detector.api.Location>()
    private val reportedCycles = mutableSetOf<String>()

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

        // Matches @type/name or ?type/name, optionally with package prefix and/or + modifier
        private val REF_PATTERN = Pattern.compile("[@?]\\+?(?:[a-zA-Z.]+:)?([a-zA-Z]+/[a-zA-Z0-9_.]+)")
    }

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? = Detector.ALL

    override fun getApplicableAttributes(): Collection<String>? = Detector.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        // Only process top-level resource definitions
        if (element.parentNode?.nodeName != "resources") return

        val tagName = element.tagName
        val nameAttr = element.getAttributeNode("name") ?: return
        val name = nameAttr.value
        if (name.isEmpty()) return

        val type = if (tagName == "item") {
            element.getAttribute("type").takeIf { it.isNotEmpty() } ?: return
        } else {
            tagName
        }

        val resId = "$type/$name"
        locations[resId] = context.getLocation(nameAttr)
        graph.putIfAbsent(resId, mutableSetOf())

        val text = element.textContent?.trim() ?: ""
        if (text.isNotEmpty()) {
            addReferences(resId, text)
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement
        // Only process attributes of top-level resource definitions
        if (element.parentNode?.nodeName != "resources") return

        val value = attribute.value
        if (value.isEmpty() || (!value.startsWith("@") && !value.startsWith("?"))) return

        val nameAttr = element.getAttributeNode("name") ?: return
        val name = nameAttr.value
        if (name.isEmpty()) return

        val type = if (element.tagName == "item") {
            element.getAttribute("type").takeIf { it.isNotEmpty() } ?: return
        } else {
            element.tagName
        }

        val resId = "$type/$name"
        addReferences(resId, value)
    }

    private fun addReferences(fromResId: String, text: String) {
        val matcher = REF_PATTERN.matcher(text)
        while (matcher.find()) {
            val ref = matcher.group(1)
            if (ref != null) {
                graph.getOrPut(fromResId) { mutableSetOf() }.add(ref)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        for (node in graph.keys.toList()) {
            if (!visited.contains(node)) {
                dfs(node, visited, recursionStack, path, context)
            }
        }
    }

    private fun dfs(
        node: String,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        path: MutableList<String>,
        context: Context
    ) {
        visited.add(node)
        recursionStack.add(node)
        path.add(node)

        for (neighbor in graph[node] ?: emptySet()) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, visited, recursionStack, path, context)
            } else if (recursionStack.contains(neighbor)) {
                val cycleStartIndex = path.indexOf(neighbor)
                if (cycleStartIndex != -1) {
                    val cycle = path.subList(cycleStartIndex, path.size)
                    reportCycle(cycle, context)
                }
            }
        }

        path.removeAt(path.size - 1)
        recursionStack.remove(node)
    }

    private fun reportCycle(cycle: List<String>, context: Context) {
        // Normalize cycle to avoid duplicate reports for the same loop starting at different nodes
        val normalized = cycle.sorted()
        val key = normalized.joinToString("->")
        if (reportedCycles.add(key)) {
            val firstRes = cycle.first()
            val location = locations[firstRes] ?: return
            val cycleStr = cycle.joinToString(" -> ") + " -> " + cycle.first()
            context.report(ISSUE, location, "Cycle detected: $cycleStr")
        }
    }
}
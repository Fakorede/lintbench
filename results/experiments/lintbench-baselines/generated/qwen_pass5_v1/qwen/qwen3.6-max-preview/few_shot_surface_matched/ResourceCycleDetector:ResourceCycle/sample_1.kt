package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private val reportedCycles = mutableSetOf<String>()
    private val refRegex = Regex("[@?][+]?([\\w.]+)/([\\w.]+)")

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val def = getDefinition(element)
        if (def != null) {
            locations[def] = context.getLocation(element.getAttributeNode("name"))
            graph.putIfAbsent(def, mutableSetOf())
            val text = element.textContent
            if (!text.isNullOrEmpty()) {
                addReferences(def, text)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        val def = getDefinition(owner)
        if (def != null && !attribute.value.isNullOrEmpty()) {
            addReferences(def, attribute.value)
        }
    }

    private fun getDefinition(element: Element): String? {
        val name = element.getAttribute("name")
        if (name.isNotEmpty() && element.parentNode?.nodeName == "resources") {
            return "${element.tagName}/$name"
        }
        return null
    }

    private fun addReferences(def: String, text: String) {
        refRegex.findAll(text).forEach { match ->
            val ref = "${match.groupValues[1]}/${match.groupValues[2]}"
            graph.getOrPut(def) { mutableSetOf() }.add(ref)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            if (node in recursionStack) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size) + node
                    val cycleKey = cycle.sorted().joinToString("|")
                    if (reportedCycles.add(cycleKey)) {
                        reportCycle(context, cycle)
                    }
                }
                return
            }
            if (node in visited) return

            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            for (neighbor in graph[node].orEmpty()) {
                dfs(neighbor)
            }

            path.removeAt(path.lastIndex)
            recursionStack.remove(node)
        }

        for (node in graph.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }

    private fun reportCycle(context: Context, cycle: List<String>) {
        val node = cycle.firstOrNull { it in locations } ?: return
        val location = locations[node] ?: return
        val cycleStr = cycle.joinToString(" -> ")
        context.report(ISSUE, location, "Cycle in resource definitions: $cycleStr")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
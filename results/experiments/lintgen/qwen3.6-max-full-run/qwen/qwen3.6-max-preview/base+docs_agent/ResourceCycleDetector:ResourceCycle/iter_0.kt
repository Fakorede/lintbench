package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val definitionLocations = mutableMapOf<String, Location>()

    override fun getApplicableElements(): Collection<String> = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.VALUES) return

        val nameAttr = element.getAttributeNode("name") ?: return
        val name = nameAttr.nodeValue ?: return
        val type = element.getAttribute("type")?.takeIf { it.isNotBlank() } ?: element.tagName
        val definedKey = "$type/$name"

        definitionLocations[definedKey] = context.getLocation(nameAttr)

        val refs = mutableSetOf<String>()
        val regex = Regex("@\\+?(\\w+)/(\\w+)")

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val value = attrs.item(i).nodeValue ?: continue
            extractRefs(value, regex, refs)
        }

        val text = element.textContent
        if (!text.isNullOrBlank()) {
            extractRefs(text, regex, refs)
        }

        graph.getOrPut(definedKey) { mutableSetOf() }.addAll(refs)
    }

    private fun extractRefs(value: String, regex: Regex, refs: MutableSet<String>) {
        regex.findAll(value).forEach { match ->
            val refType = match.groupValues[1]
            val refName = match.groupValues[2]
            if (refType != "id" && refType != "android") {
                refs.add("$refType/$refName")
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (graph.isEmpty()) return

        val state = mutableMapOf<String, State>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<String>()

        for (node in graph.keys.toList()) {
            if (state[node] != State.VISITED) {
                dfs(node, state, path, reportedCycles, context)
            }
        }

        graph.clear()
        definitionLocations.clear()
    }

    private enum class State { UNVISITED, VISITING, VISITED }

    private fun dfs(
        node: String,
        state: MutableMap<String, State>,
        path: MutableList<String>,
        reportedCycles: MutableSet<String>,
        context: Context
    ) {
        when (state[node]) {
            State.VISITED -> return
            State.VISITING -> {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size) + node
                    val cycleKey = cycle.sorted().joinToString("->")
                    if (reportedCycles.add(cycleKey)) {
                        val reportNode = cycle.firstOrNull { definitionLocations.containsKey(it) } ?: node
                        val location = definitionLocations[reportNode] ?: return
                        val message = "Cycle in resource definitions: ${cycle.joinToString(" -> ")}"
                        context.report(ISSUE, location, message)
                    }
                }
                return
            }
            else -> {}
        }

        state[node] = State.VISITING
        path.add(node)

        for (neighbor in graph[node].orEmpty()) {
            dfs(neighbor, state, path, reportedCycles, context)
        }

        path.removeAt(path.lastIndex)
        state[node] = State.VISITED
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
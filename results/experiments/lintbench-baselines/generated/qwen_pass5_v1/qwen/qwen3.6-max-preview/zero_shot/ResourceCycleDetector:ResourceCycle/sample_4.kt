package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.io.File

class ResourceCycleDetector : Detector(), Detector.XmlScanner {

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

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val edgeLocations = mutableMapOf<String, MutableMap<String, Location>>()

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        edgeLocations.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val sourceKeys = getSourceResourceKeys(context, element)
        if (sourceKeys.isEmpty()) return

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value
            val regex = Regex("@(?:\\+)?(?:\\w+:)?(\\w+)/(\\w+)")
            regex.findAll(value).forEach { matchResult ->
                val type = matchResult.groupValues[1]
                val name = matchResult.groupValues[2]
                if (type == "id") return@forEach
                val targetKey = "$type/$name"
                val location = context.getLocation(attr)

                for (sourceKey in sourceKeys) {
                    graph.getOrPut(sourceKey) { mutableSetOf() }.add(targetKey)
                    edgeLocations.getOrPut(sourceKey) { mutableMapOf() }[targetKey] = location
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            val currentState = state[node] ?: 0
            if (currentState == 2) return
            if (currentState == 1) {
                val cycleStartIndex = path.indexOf(node)
                if (cycleStartIndex != -1) {
                    val cycle = path.subList(cycleStartIndex, path.size) + node
                    val from = cycle[cycle.size - 2]
                    val to = cycle[cycle.size - 1]
                    val location = edgeLocations[from]?.get(to) ?: return
                    val cycleStr = cycle.joinToString(" -> ")
                    context.report(ISSUE, location, "Resource cycle detected: $cycleStr")
                }
                return
            }

            state[node] = 1
            path.add(node)
            for (neighbor in graph[node] ?: emptySet()) {
                dfs(neighbor)
            }
            path.removeAt(path.size - 1)
            state[node] = 2
        }

        for (node in graph.keys.toList()) {
            if (state[node] != 2) {
                dfs(node)
            }
        }
    }

    private fun getSourceResourceKeys(context: XmlContext, element: Element): List<String> {
        val parent = context.file.parentFile ?: return emptyList()
        val folderName = parent.name
        val baseFolder = folderName.substringBefore('-')
        val fileName = context.file.nameWithoutExtension

        return if (baseFolder == "values") {
            val nameAttr = element.getAttribute("name")
            if (nameAttr.isNotEmpty()) {
                val typeAttr = element.getAttribute("type")
                val type = if (typeAttr.isNotEmpty()) typeAttr else element.tagName
                listOf("$type/$nameAttr")
            } else {
                emptyList()
            }
        } else {
            listOf("$baseFolder/$fileName")
        }
    }
}
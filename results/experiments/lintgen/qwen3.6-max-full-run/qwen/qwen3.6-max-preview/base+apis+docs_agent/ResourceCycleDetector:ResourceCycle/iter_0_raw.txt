package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import java.util.regex.Pattern

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val defLocations = mutableMapOf<String, Location>()
    private val refLocations = mutableMapOf<Pair<String, String>, Location>()
    private val refPattern = Pattern.compile("@\\+?(\\w+)/(\\w+)")

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val currentRes = getCurrentResource(context, folderType, element) ?: return

        defLocations.putIfAbsent(currentRes, context.getLocation(element))

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val value = attributes.item(i).nodeValue ?: continue
            extractRefs(value, currentRes, context.getLocation(element))
        }

        val text = element.textContent
        if (!text.isNullOrBlank()) {
            extractRefs(text, currentRes, context.getLocation(element))
        }
    }

    private fun getCurrentResource(context: XmlContext, folderType: ResourceFolderType, element: Element): String? {
        return if (folderType == ResourceFolderType.VALUES) {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) "${element.tagName}/$name" else null
        } else {
            val fileName = context.file.name.substringBefore('.', context.file.name)
            "${folderType.getName()}/$fileName"
        }
    }

    private fun extractRefs(text: String, currentRes: String, location: Location) {
        val matcher = refPattern.matcher(text)
        while (matcher.find()) {
            val type = matcher.group(1)
            val name = matcher.group(2)
            val refRes = "$type/$name"
            graph.getOrPut(currentRes) { mutableSetOf() }.add(refRes)
            refLocations.putIfAbsent(currentRes to refRes, location)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String) {
            state[node] = 1
            path.add(node)
            for (neighbor in graph[node] ?: emptySet()) {
                if (state[neighbor] == 1) {
                    val cycleStartIdx = path.indexOf(neighbor)
                    if (cycleStartIdx != -1) {
                        val cycle = path.subList(cycleStartIdx, path.size) + neighbor
                        val cycleKey = cycle.sorted().joinToString("|")
                        if (reported.add(cycleKey)) {
                            val cycleStr = cycle.joinToString(" -> ")
                            val loc = refLocations[node to neighbor] ?: defLocations[node] ?: Location.create(context.file)
                            context.report(ISSUE, loc, "Resource cycle detected: $cycleStr")
                        }
                    }
                } else if (state[neighbor] == 0) {
                    dfs(neighbor)
                }
            }
            path.removeAt(path.lastIndex)
            state[node] = 2
        }

        for (node in graph.keys.toList()) {
            if (state[node] == 0) {
                dfs(node)
            }
        }

        graph.clear()
        defLocations.clear()
        refLocations.clear()
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
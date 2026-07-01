package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.regex.Pattern

class ResourceCycleDetector : ResourceXmlDetector() {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private val refLocations = mutableMapOf<String, Location>()

    companion object {
        @JvmField
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

        private val RESOURCE_REF_PATTERN = Pattern.compile("@(\\+)?(\\w+:)?(\\w+)/(\\w+)")
    }

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        refLocations.clear()
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val folderName = folderType.getName()
        val sourceRes = getSourceResource(context, folderName, folderType, element) ?: return

        if (!locations.containsKey(sourceRes)) {
            locations[sourceRes] = context.getLocation(element)
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value
            if ('@' in value) {
                val matcher = RESOURCE_REF_PATTERN.matcher(value)
                while (matcher.find()) {
                    // Skip references to external packages (e.g., @android:color/white)
                    if (matcher.group(2) != null) continue

                    val type = matcher.group(3)
                    val name = matcher.group(4)

                    // ID references do not cause inflation cycles
                    if (type == "id") continue

                    val targetRes = "$type/$name"
                    graph.getOrPut(sourceRes) { mutableSetOf() }.add(targetRes)
                    refLocations["$sourceRes->$targetRes"] = context.getLocation(attr)
                }
            }
        }
    }

    private fun getSourceResource(
        context: XmlContext,
        folderName: String,
        folderType: ResourceFolderType,
        element: Element
    ): String? {
        val tagName = element.tagName

        if (folderType == ResourceFolderType.VALUES) {
            if (tagName == "resources") return null
            val name = element.getAttribute("name")
            if (name.isEmpty()) return null

            val type = when (tagName) {
                "item" -> element.getAttribute("type").takeIf { it.isNotEmpty() } ?: return null
                "declare-styleable", "attr", "public", "java-symbol" -> return null
                else -> tagName
            }
            return "$type/$name"
        }

        // File-based resources (drawable, layout, anim, etc.)
        val fileName = context.file.nameWithoutExtension
        return "$folderName/$fileName"
    }

    override fun afterCheckRootProject(context: Context) {
        detectCycles(context)
    }

    private fun detectCycles(context: Context) {
        val state = mutableMapOf<String, Int>() // 0: unvisited, 1: visiting, 2: visited
        val path = mutableListOf<String>()
        val reportedCycleSets = mutableSetOf<Set<String>>()

        fun dfs(node: String) {
            val s = state[node] ?: 0
            if (s == 2) return
            if (s == 1) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    val cycleNodes = path.subList(cycleStart, path.size).toSet()
                    if (reportedCycleSets.add(cycleNodes)) {
                        val cycleList = path.subList(cycleStart, path.size)
                        reportCycle(context, cycleList, node)
                    }
                }
                return
            }

            state[node] = 1
            path.add(node)

            for (neighbor in graph[node].orEmpty()) {
                dfs(neighbor)
            }

            path.removeAt(path.lastIndex)
            state[node] = 2
        }

        for (node in graph.keys) {
            if (state[node] != 2) {
                dfs(node)
            }
        }
    }

    private fun reportCycle(context: Context, cycle: List<String>, closingNode: String) {
        val cycleStr = cycle.joinToString(" -> ") + " -> ${cycle.first()}"
        val refKey = "$closingNode->${cycle.first()}"
        val location = refLocations[refKey] ?: locations[closingNode] ?: Location.create(context.file)

        context.report(
            ISSUE,
            location,
            "Resource cycle detected: $cycleStr"
        )
    }
}
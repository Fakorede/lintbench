package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val parentMap = mutableMapOf<String, String>()
    private val locations = mutableMapOf<String, Location>()

    override fun beforeCheckRootProject(context: Context) {
        parentMap.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("style")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("parent")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttribute("name") ?: return
        if (name.isEmpty()) return

        if (!element.hasAttribute("parent")) {
            val lastDot = name.lastIndexOf('.')
            if (lastDot > 0) {
                val parent = name.substring(0, lastDot)
                parentMap[name] = parent
                locations[name] = context.getLocation(element)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        if (attribute.name == "parent") {
            val element = attribute.ownerElement ?: return
            val name = element.getAttribute("name") ?: return
            if (name.isNotEmpty()) {
                var parentValue = attribute.value
                if (parentValue.startsWith("@style/")) {
                    parentValue = parentValue.substring("@style/".length)
                } else if (parentValue.startsWith("@android:style/")) {
                    parentValue = parentValue.substring("@android:style/".length)
                }
                parentMap[name] = parentValue
                locations[name] = context.getLocation(attribute)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        val cycles = mutableSetOf<List<String>>()

        fun dfs(node: String, path: List<String>) {
            if (node in stack) {
                val cycleStartIndex = path.indexOf(node)
                if (cycleStartIndex != -1) {
                    cycles.add(path.subList(cycleStartIndex, path.size))
                }
                return
            }
            if (node in visited) return

            visited.add(node)
            stack.add(node)

            val parent = parentMap[node]
            if (parent != null) {
                dfs(parent, path + node)
            }

            stack.remove(node)
        }

        for (node in parentMap.keys) {
            if (node !in visited) {
                dfs(node, emptyList())
            }
        }

        for (cycle in cycles) {
            if (cycle.isEmpty()) continue
            val firstWithLocation = cycle.firstOrNull { locations.containsKey(it) } ?: cycle[0]
            val location = locations[firstWithLocation] ?: continue
            val cycleString = cycle.joinToString(" -> ") + " -> " + cycle[0]
            context.report(
                ISSUE,
                location,
                "Cycle detected in resource definitions: $cycleString"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
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

    private val definitions = mutableMapOf<String, MutableList<Definition>>()

    private class Definition(
        val name: String,
        val dependencies: List<String>,
        val location: Location
    )

    override fun beforeCheckRootProject(context: Context) {
        definitions.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("resources")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "resources") return

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val name = child.getAttribute("name")
                if (name.isEmpty()) continue

                var type = child.tagName
                if (type == "item") {
                    type = child.getAttribute("type")
                    if (type.isEmpty()) continue
                }

                val resourceKey = "@$type/$name"
                val dependencies = mutableListOf<String>()

                if (type == "style") {
                    if (child.hasAttribute("parent")) {
                        val parentAttr = child.getAttribute("parent")
                        if (parentAttr.isNotEmpty()) {
                            val parentRef = normalizeResourceRef(parentAttr, "style")
                            if (parentRef != null) {
                                dependencies.add(parentRef)
                            }
                        }
                    } else if (name.contains('.')) {
                        val lastDot = name.lastIndexOf('.')
                        val parentName = name.substring(0, lastDot)
                        dependencies.add("@style/$parentName")
                    }
                } else {
                    val text = child.textContent.trim()
                    if (text.startsWith("@")) {
                        val ref = normalizeResourceRef(text)
                        if (ref != null) {
                            dependencies.add(ref)
                        }
                    }
                }

                val location = context.getLocation(child)
                definitions.getOrPut(resourceKey) { mutableListOf() }.add(
                    Definition(resourceKey, dependencies, location)
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): List<String>? {
            if (visiting.contains(node)) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    return path.subList(cycleStart, path.size) + node
                }
                return listOf(node, node)
            }
            if (visited.contains(node)) {
                return null
            }

            visiting.add(node)
            path.add(node)

            val defs = definitions[node]
            if (defs != null) {
                for (def in defs) {
                    for (dep in def.dependencies) {
                        val cycle = dfs(dep)
                        if (cycle != null) {
                            return cycle
                        }
                    }
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(node)
            visited.add(node)
            return null
        }

        for (resourceName in definitions.keys) {
            visiting.clear()
            path.clear()
            val cycle = dfs(resourceName)
            if (cycle != null) {
                val cycleString = cycle.joinToString(" -> ")
                val message = "Cycle detected in resource definitions: $cycleString"
                val defs = definitions[resourceName]
                if (defs != null) {
                    for (def in defs) {
                        context.report(ISSUE, def.location, message)
                    }
                }
                visited.addAll(cycle)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No-op
    }

    private fun normalizeResourceRef(ref: String, defaultType: String? = null): String? {
        val trimmed = ref.trim()
        if (trimmed.startsWith("@")) {
            if (trimmed.startsWith("@+")) {
                return null
            }
            val parts = trimmed.substring(1).split('/')
            if (parts.size == 2) {
                val typeAndPkg = parts[0]
                val name = parts[1]
                val type = typeAndPkg.substringAfter(':')
                return "@$type/$name"
            }
        } else if (defaultType != null && trimmed.isNotEmpty() && !trimmed.startsWith("?")) {
            val clean = trimmed.substringAfter(':')
            return "@$defaultType/$clean"
        }
        return null
    }
}
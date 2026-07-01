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
import org.w3c.dom.CharacterData
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector() {

    private val definitions = mutableMapOf<String, ResourceDef>()

    private class ResourceDef(
        val type: String,
        val name: String,
        val locations: MutableList<Location> = mutableListOf(),
        val dependencies: MutableSet<String> = mutableSetOf()
    ) {
        val fqName: String get() = "$type/$name"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return null // Visit all elements in values files
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode ?: return
        if (parent.nodeName != "resources") return

        val name = element.getAttribute("name")
        if (name.isNullOrEmpty()) return

        val tag = element.tagName
        val type = if (tag == "item") element.getAttribute("type") else tag
        if (type.isNullOrEmpty()) return

        val dependencies = mutableSetOf<String>()
        val texts = mutableListOf<String>()
        collectTextsAndAttributes(element, texts)

        for (text in texts) {
            RESOURCE_REF_REGEX.findAll(text).forEach { match ->
                val refType = match.groupValues[1]
                val refName = match.groupValues[2]
                dependencies.add("$refType/$refName")
            }
        }

        if (type == "style") {
            if (element.hasAttribute("parent")) {
                val parentAttr = element.getAttribute("parent")
                if (parentAttr.isNotEmpty()) {
                    if (!parentAttr.startsWith("@") && !parentAttr.startsWith("android:")) {
                        dependencies.add("style/$parentAttr")
                    }
                }
            } else {
                val lastDot = name.lastIndexOf('.')
                if (lastDot != -1) {
                    val parentName = name.substring(0, lastDot)
                    dependencies.add("style/$parentName")
                }
            }
        }

        val fqName = "$type/$name"
        val location = context.getLocation(element)
        val existing = definitions[fqName]
        if (existing != null) {
            existing.locations.add(location)
            existing.dependencies.addAll(dependencies)
        } else {
            definitions[fqName] = ResourceDef(type, name, mutableListOf(location), dependencies)
        }
    }

    private fun collectTextsAndAttributes(node: Node, results: MutableList<String>) {
        if (node is Element) {
            val attributes = node.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                results.add(attr.nodeValue)
            }
            var child = node.firstChild
            while (child != null) {
                collectTextsAndAttributes(child, results)
                child = child.nextSibling
            }
        } else if (node is CharacterData) {
            results.add(node.data)
        }
    }

    override fun afterCheckProject(context: Context) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<String>()

        val sortedKeys = definitions.keys.sorted()

        for (key in sortedKeys) {
            if (!visited.contains(key)) {
                dfs(key, path, visiting, visited, reportedCycles, context)
            }
        }

        definitions.clear()
    }

    private fun dfs(
        node: String,
        path: MutableList<String>,
        visiting: MutableSet<String>,
        visited: MutableSet<String>,
        reportedCycles: MutableSet<String>,
        context: Context
    ) {
        if (visiting.contains(node)) {
            val cycleStartIndex = path.indexOf(node)
            if (cycleStartIndex != -1) {
                val cycleList = path.subList(cycleStartIndex, path.size) + node
                val canonicalCycle = cycleList.distinct().sorted().joinToString(",")
                if (reportedCycles.add(canonicalCycle)) {
                    val cycleString = cycleList.joinToString(" -> ")
                    val def = definitions[node]
                    if (def != null) {
                        for (location in def.locations) {
                            context.report(
                                ISSUE,
                                location,
                                "Cycle detected in resource definitions: $cycleString"
                            )
                        }
                    }
                }
            }
            return
        }

        if (visited.contains(node)) {
            return
        }

        visiting.add(node)
        path.add(node)

        val def = definitions[node]
        if (def != null) {
            for (dep in def.dependencies) {
                dfs(dep, path, visiting, visited, reportedCycles, context)
            }
        }

        path.removeAt(path.size - 1)
        visiting.remove(node)
        visited.add(node)
    }

    companion object {
        private val RESOURCE_REF_REGEX = Regex("""@(?!(?:android|system):)([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)""")

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
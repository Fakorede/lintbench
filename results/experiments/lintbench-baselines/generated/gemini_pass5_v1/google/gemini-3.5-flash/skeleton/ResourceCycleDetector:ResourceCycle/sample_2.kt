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

    private class ResourceNode(
        val url: String,
        val location: Location,
        val dependencies: MutableSet<String> = mutableSetOf()
    )

    private val nodes = mutableMapOf<String, ResourceNode>()

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions (such as style parents or value references) as this can lead to runtime exceptions or build failures.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private val RESOURCE_REF_PATTERN = Regex("""[?@](?:([a-zA-Z0-9_]+):)?([a-zA-Z]+)/([a-zA-Z0-9_.]+)""")
    }

    override fun beforeCheckRootProject(context: Context) {
        nodes.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
        if (name.isEmpty()) return

        val tagName = element.tagName
        if (tagName == "resources") return

        val type = when (tagName) {
            "item" -> {
                val t = element.getAttribute("type")
                if (t.isEmpty()) return else t
            }
            "string-array", "integer-array" -> "array"
            else -> tagName
        }

        val url = "@$type/$name"
        val node = nodes.getOrPut(url) {
            ResourceNode(url, context.getLocation(element))
        }

        val text = element.textContent
        if (text != null) {
            val matches = RESOURCE_REF_PATTERN.findAll(text)
            for (match in matches) {
                val ns = match.groupValues[1]
                if (ns == "android") continue
                val depType = match.groupValues[2]
                val depName = match.groupValues[3]
                val depUrl = "@$depType/$depName"
                if (depUrl != url) {
                    node.dependencies.add(depUrl)
                }
            }
        }

        if (tagName == "style") {
            val parent = getStyleParent(element)
            if (parent != null && parent != url) {
                node.dependencies.add(parent)
            }
        }
    }

    private fun getStyleParent(element: Element): String? {
        val parentAttr = element.getAttribute("parent")
        if (parentAttr.isNotEmpty()) {
            if (parentAttr.startsWith("android:") || parentAttr.startsWith("@android:")) {
                return null
            }
            if (parentAttr.startsWith("@style/")) {
                return parentAttr
            }
            return "@style/$parentAttr"
        }
        val name = element.getAttribute("name")
        if (name.contains('.')) {
            val parentName = name.substringBeforeLast('.')
            return "@style/$parentName"
        }
        return null
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<String>()

        fun dfs(nodeUrl: String) {
            if (visiting.contains(nodeUrl)) {
                val cycleStartIndex = path.indexOf(nodeUrl)
                if (cycleStartIndex != -1) {
                    val cycleList = path.subList(cycleStartIndex, path.size).toList()
                    val normalized = normalizeCycle(cycleList)
                    val cycleString = normalized.joinToString(" -> ") + " -> ${normalized.first()}"
                    if (reportedCycles.add(cycleString)) {
                        var reported = false
                        for (url in cycleList) {
                            val node = nodes[url]
                            if (node != null) {
                                val message = "Cycle in resource definitions: $cycleString"
                                context.report(ISSUE, node.location, message)
                                reported = true
                                break
                            }
                        }
                        if (!reported) {
                            context.report(ISSUE, Location.create(context.file), "Cycle in resource definitions: $cycleString")
                        }
                    }
                }
                return
            }

            if (visited.contains(nodeUrl)) {
                return
            }

            visiting.add(nodeUrl)
            path.add(nodeUrl)

            val node = nodes[nodeUrl]
            if (node != null) {
                for (dep in node.dependencies) {
                    dfs(dep)
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(nodeUrl)
            visited.add(nodeUrl)
        }

        for (url in nodes.keys) {
            if (!visited.contains(url)) {
                dfs(url)
            }
        }
    }

    private fun normalizeCycle(cycle: List<String>): List<String> {
        if (cycle.isEmpty()) return cycle
        var minIndex = 0
        var minVal = cycle[0]
        for (i in 1 until cycle.size) {
            if (cycle[i] < minVal) {
                minVal = cycle[i]
                minIndex = i
            }
        }
        val result = ArrayList<String>(cycle.size)
        for (i in cycle.indices) {
            result.add(cycle[(minIndex + i) % cycle.size])
        }
        return result
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Handled in visitElement
    }
}
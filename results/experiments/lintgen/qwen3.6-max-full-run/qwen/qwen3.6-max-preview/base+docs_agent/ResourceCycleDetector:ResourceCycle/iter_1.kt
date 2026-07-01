package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : Detector(), XmlScanner {

    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val isRoot = element.parentNode == null || element == context.document.documentElement

        var definedKey: String? = null

        if (folderType == ResourceFolderType.VALUES) {
            val nameAttr = element.getAttributeNode("name")
            if (nameAttr != null) {
                val name = nameAttr.value
                val type = if (element.tagName == "item") element.getAttribute("type") else element.tagName
                if (type.isNotBlank() && name.isNotBlank()) {
                    definedKey = "$type/$name"
                    locations[definedKey] = context.getLocation(nameAttr)
                    dependencies.getOrPut(definedKey) { mutableSetOf() }
                }
            }
        } else if (isRoot) {
            val type = folderType.getName()
            val name = context.file.nameWithoutExtension
            definedKey = "$type/$name"
            locations[definedKey] = context.getLocation(element)
            dependencies.getOrPut(definedKey) { mutableSetOf() }
        }

        val refs = mutableSetOf<String>()

        if (element.tagName == "style") {
            val parentAttr = element.getAttributeNode("parent")
            if (parentAttr != null && parentAttr.value.isNotBlank()) {
                val parentKey = parseResourceRef(parentAttr.value, "style")
                if (parentKey != null) refs.add(parentKey)
            } else {
                val nameAttr = element.getAttributeNode("name")
                if (nameAttr != null) {
                    val name = nameAttr.value
                    val dotIndex = name.lastIndexOf('.')
                    if (dotIndex > 0) {
                        refs.add("style/${name.substring(0, dotIndex)}")
                    }
                }
            }
        }

        if (element.tagName == "include") {
            val layoutAttr = element.getAttributeNode("layout")
            if (layoutAttr != null && layoutAttr.value.isNotBlank()) {
                val ref = parseResourceRef(layoutAttr.value, "layout")
                if (ref != null) refs.add(ref)
            }
        }

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val value = attr.value
            if (value.indexOf('@') != -1 || value.indexOf('?') != -1) {
                extractRefsFromString(value, refs)
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                val text = child.nodeValue
                if (text != null && (text.indexOf('@') != -1 || text.indexOf('?') != -1)) {
                    extractRefsFromString(text, refs)
                }
            }
            child = child.nextSibling
        }

        if (definedKey != null && refs.isNotEmpty()) {
            dependencies[definedKey]!!.addAll(refs)
        }
    }

    private fun parseResourceRef(value: String, defaultType: String): String? {
        var clean = value.trim()
        if (clean.startsWith("@") || clean.startsWith("?")) {
            clean = clean.substring(1)
        }
        if (clean.startsWith("+")) {
            clean = clean.substring(1)
        }
        val slashIndex = clean.indexOf('/')
        val type: String
        val name: String
        if (slashIndex > 0) {
            val typePart = clean.substring(0, slashIndex)
            name = clean.substring(slashIndex + 1)
            type = typePart.substringAfter(':')
        } else {
            name = clean
            type = defaultType
        }
        if (name.isBlank() || name == "null") return null
        return "$type/$name"
    }

    private fun extractRefsFromString(value: String, refs: MutableSet<String>) {
        val regex = Regex("[@?]\\+?([\\w:]+)/([\\w.]+)")
        regex.findAll(value).forEach { match ->
            val typePart = match.groupValues[1]
            val name = match.groupValues[2]
            if (name == "null") return@forEach
            val type = typePart.substringAfter(':')
            if (type != "id" && type != "android") {
                refs.add("$type/$name")
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (dependencies.isEmpty()) return

        val state = mutableMapOf<String, State>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<String>()

        for (node in dependencies.keys.toList()) {
            if (state[node] != State.VISITED) {
                dfs(node, state, path, reportedCycles, context)
            }
        }

        dependencies.clear()
        locations.clear()
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
                        val reportNode = cycle.firstOrNull { locations.containsKey(it) } ?: node
                        val location = locations[reportNode] ?: return
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

        for (neighbor in dependencies[node].orEmpty()) {
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
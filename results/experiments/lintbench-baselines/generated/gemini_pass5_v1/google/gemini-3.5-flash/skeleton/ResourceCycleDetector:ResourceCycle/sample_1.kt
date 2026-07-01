package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    private val definitions = mutableMapOf<String, ResourceDefinition>()

    private class ResourceDefinition(
        val url: String,
        val location: com.android.tools.lint.detector.api.Location,
        val dependencies: MutableSet<String> = mutableSetOf()
    )

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

        private val RESOURCE_REF_REGEX = """@(?:[a-zA-Z0-9_]+:)?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)""".toRegex()
    }

    override fun beforeCheckRootProject(context: Context) {
        definitions.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "style", "dimen", "color", "string", "integer", "bool", "item",
            "array", "string-array", "integer-array", "plurals", "fraction",
            "drawable", "include"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        if (folderType == ResourceFolderType.VALUES) {
            val parent = element.parentNode
            if (parent != null && parent.nodeName == "resources") {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    val type = if (element.nodeName == "item") {
                        element.getAttribute("type")
                    } else {
                        element.nodeName
                    }
                    if (type.isNotEmpty()) {
                        val normalizedType = when (type) {
                            "string-array", "integer-array" -> "array"
                            else -> type
                        }
                        val url = "@$normalizedType/$name"
                        val definition = definitions.getOrPut(url) {
                            ResourceDefinition(url, context.getLocation(element))
                        }
                        val text = element.textContent
                        if (text != null) {
                            val matches = RESOURCE_REF_REGEX.findAll(text)
                            for (match in matches) {
                                definition.dependencies.add(match.value)
                            }
                        }
                        if (normalizedType == "style") {
                            if (element.hasAttribute("parent")) {
                                val parentVal = element.getAttribute("parent")
                                if (parentVal.isNotEmpty()) {
                                    definition.dependencies.add(resolveStyleParent(parentVal))
                                }
                            } else if (name.contains(".")) {
                                val parentName = name.substringBeforeLast(".")
                                definition.dependencies.add("@style/$parentName")
                            }
                        }
                    }
                }
            }
        } else if (folderType == ResourceFolderType.LAYOUT) {
            if (element.nodeName == "include") {
                val layoutVal = if (element.hasAttribute("layout")) {
                    element.getAttribute("layout")
                } else if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "layout")) {
                    element.getAttributeNS("http://schemas.android.com/apk/res/android", "layout")
                } else {
                    ""
                }
                if (layoutVal.isNotEmpty() && layoutVal.startsWith("@layout/")) {
                    val layoutName = context.file.nameWithoutExtension
                    val url = "@layout/$layoutName"
                    val definition = definitions.getOrPut(url) {
                        ResourceDefinition(url, context.getLocation(element))
                    }
                    definition.dependencies.add(layoutVal)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    override fun afterCheckRootProject(context: Context) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val cycles = mutableListOf<List<String>>()

        fun dfs(node: String, path: List<String>) {
            if (node in visiting) {
                val cycleStartIndex = path.indexOf(node)
                if (cycleStartIndex != -1) {
                    cycles.add(path.subList(cycleStartIndex, path.size) + node)
                }
                return
            }
            if (node in visited) {
                return
            }
            visiting.add(node)
            val def = definitions[node]
            if (def != null) {
                for (dep in def.dependencies) {
                    dfs(dep, path + node)
                }
            }
            visiting.remove(node)
            visited.add(node)
        }

        for (node in definitions.keys) {
            if (node !in visited) {
                dfs(node, emptyList())
            }
        }

        val uniqueCycles = cycles.map { normalizeCycle(it) }.distinct()

        for (cycle in uniqueCycles) {
            for (node in cycle) {
                val def = definitions[node]
                if (def != null) {
                    val cycleStr = cycle.joinToString(" -> ")
                    context.report(
                        ISSUE,
                        def.location,
                        "Cycle detected in resource definitions: $cycleStr"
                    )
                }
            }
        }
    }

    private fun resolveStyleParent(parentVal: String): String {
        return when {
            parentVal.startsWith("@style/") || parentVal.startsWith("@android:style/") -> parentVal
            parentVal.startsWith("@") || parentVal.startsWith("?") -> parentVal
            parentVal.startsWith("android:") -> "@android:style/${parentVal.removePrefix("android:")}"
            else -> "@style/$parentVal"
        }
    }

    private fun normalizeCycle(cycle: List<String>): List<String> {
        val unique = cycle.dropLast(1)
        if (unique.isEmpty()) return cycle
        val minIndex = unique.indices.minByOrNull { unique[it] } ?: 0
        val shifted = unique.subList(minIndex, unique.size) + unique.subList(0, minIndex)
        return shifted + shifted.first()
    }
}
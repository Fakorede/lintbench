package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to runtime exceptions.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val referencePattern = Regex("""@[+]?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)""")
    }

    private data class ResourceDef(
        val url: String,
        val location: Location,
        val dependencies: Set<String>
    )

    private val definitions = mutableMapOf<String, ResourceDef>()

    override fun beforeCheckProject(context: Context) {
        definitions.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "resources") return

        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                val tag = element.tagName
                val type = if (tag == "item") element.getAttribute("type") else tag
                val name = element.getAttribute("name")

                if (!type.isNullOrEmpty() && !name.isNullOrEmpty()) {
                    val url = "@$type/$name"
                    val location = context.getLocation(element)
                    val dependencies = mutableSetOf<String>()

                    if (type == "style") {
                        val parent = element.getAttribute("parent")
                        if (!parent.isNullOrEmpty()) {
                            val normalizedParent = if (!parent.startsWith("@")) {
                                "@style/$parent"
                            } else {
                                parent
                            }
                            dependencies.add(normalizedParent)
                        } else if (name.contains('.')) {
                            val lastDot = name.lastIndexOf('.')
                            val parentName = name.substring(0, lastDot)
                            dependencies.add("@style/$parentName")
                        }

                        var itemChild = element.firstChild
                        while (itemChild != null) {
                            if (itemChild.nodeType == Node.ELEMENT_NODE) {
                                val itemElement = itemChild as Element
                                if (itemElement.tagName == "item") {
                                    dependencies.addAll(extractReferences(itemElement.textContent))
                                }
                            }
                            itemChild = itemChild.nextSibling
                        }
                    } else {
                        dependencies.addAll(extractReferences(element.textContent))
                    }

                    definitions[url] = ResourceDef(url, location, dependencies)
                }
            }
            child = child.nextSibling
        }
    }

    override fun afterCheckProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(url: String) {
            if (url in visiting) {
                val cycleStartIndex = path.indexOf(url)
                if (cycleStartIndex != -1) {
                    val cyclePath = path.subList(cycleStartIndex, path.size) + url
                    val cycleSet = cyclePath.toSet()
                    if (reportedCycles.add(cycleSet)) {
                        val firstDef = cyclePath.mapNotNull { definitions[it] }.firstOrNull()
                        if (firstDef != null) {
                            val pathString = cyclePath.joinToString(" -> ")
                            context.report(
                                ISSUE,
                                firstDef.location,
                                "Cycle detected in resource definitions: $pathString"
                            )
                        }
                    }
                }
                return
            }
            if (url in visited) {
                return
            }

            visiting.add(url)
            path.add(url)

            val def = definitions[url]
            if (def != null) {
                for (dep in def.dependencies) {
                    dfs(dep)
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(url)
            visited.add(url)
        }

        for (url in definitions.keys) {
            if (url !in visited) {
                dfs(url)
            }
        }
    }

    private fun extractReferences(text: String?): List<String> {
        if (text == null) return emptyList()
        return referencePattern.findAll(text).map { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            "@$type/$name"
        }.toList()
    }
}
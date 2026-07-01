package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class ResourceCycleDetector : Detector(), XmlScanner {

    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()

    override fun beforeCheckProject(context: Context) {
        dependencies.clear()
        locations.clear()
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType
        if (folderType == ResourceFolderType.VALUES) {
            if (element.parentNode?.nodeName == "resources") {
                val tagName = element.tagName
                val name = element.getAttribute("name")
                if (!name.isNullOrEmpty()) {
                    val type = if (tagName == "item") {
                        element.getAttribute("type") ?: ""
                    } else {
                        tagName
                    }
                    if (type.isNotEmpty()) {
                        val resId = "$type/$name"
                        locations[resId] = context.getLocation(element)

                        val refs = mutableSetOf<String>()
                        val attributes = element.attributes
                        for (i in 0 until attributes.length) {
                            val attr = attributes.item(i)
                            refs.addAll(extractReferences(attr.nodeValue))
                        }
                        refs.addAll(extractReferences(element.textContent))

                        if (tagName == "style") {
                            val parentAttr = element.getAttribute("parent")
                            if (!parentAttr.isNullOrEmpty()) {
                                val parentRef = if (parentAttr.startsWith("@")) {
                                    extractReferences(parentAttr).firstOrNull()
                                } else {
                                    "style/$parentAttr"
                                }
                                if (parentRef != null) {
                                    refs.add(parentRef)
                                }
                            }
                        }

                        if (refs.isNotEmpty()) {
                            dependencies.getOrPut(resId) { mutableSetOf() }.addAll(refs)
                        }
                    }
                }
            }
        } else if (folderType != null) {
            val resId = "${folderType.name}/${context.file.nameWithoutExtension}"
            if (!locations.containsKey(resId)) {
                locations[resId] = context.getLocation(element)
            }

            val refs = mutableSetOf<String>()
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                refs.addAll(extractReferences(attr.nodeValue))
            }
            if (refs.isNotEmpty()) {
                dependencies.getOrPut(resId) { mutableSetOf() }.addAll(refs)
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            visiting.add(node)
            path.add(node)

            val targets = dependencies[node] ?: emptySet()
            for (target in targets) {
                if (target in visiting) {
                    val cycleStartIndex = path.indexOf(target)
                    val cycle = path.subList(cycleStartIndex, path.size).toList()
                    val cycleStr = (cycle + target).joinToString(" -> ")
                    val location = locations[node] ?: locations[target] ?: Location.create(context.file)
                    context.report(
                        ISSUE,
                        location,
                        "Cycle detected in resource definitions: $cycleStr"
                    )
                } else if (target !in visited) {
                    dfs(target)
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(node)
            visited.add(node)
        }

        for (node in dependencies.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }

    private fun extractReferences(text: String): List<String> {
        return RESOURCE_REF_PATTERN.findAll(text).map { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            "$type/$name"
        }.toList()
    }

    companion object {
        private val RESOURCE_REF_PATTERN = Regex("""[?@](?:[a-zA-Z0-9_.]+?:)?([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.]+)""")

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
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }
}
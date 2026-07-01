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
import org.w3c.dom.Document
import org.w3c.dom.Element

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val REFS_REGEX = Regex("""@[a-zA-Z0-9_.:]+/[a-zA-Z0-9_]+""")
    }

    private class ResourceNode(
        val url: String,
        var location: Location,
        val references: MutableSet<String> = mutableSetOf()
    )

    private val nodes = mutableMapOf<String, ResourceNode>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val name = child.getAttribute("name")
                if (name.isEmpty()) continue

                var type = child.tagName
                if (type == "item") {
                    val typeAttr = child.getAttribute("type")
                    if (typeAttr.isNotEmpty()) {
                        type = typeAttr
                    }
                }

                val url = "@$type/$name"
                val location = context.getLocation(child)
                val node = nodes.getOrPut(url) { ResourceNode(url, location) }
                node.location = location

                val attributes = child.attributes
                for (j in 0 until attributes.length) {
                    val attr = attributes.item(j)
                    findReferences(attr.nodeValue, node.references)
                }

                findReferences(child.textContent, node.references)

                if (type == "style") {
                    val parent = child.getAttribute("parent")
                    if (parent.isNotEmpty()) {
                        val parentUrl = if (parent.startsWith("@")) parent else "@style/$parent"
                        node.references.add(parentUrl)
                    } else if (name.contains(".")) {
                        val lastDot = name.lastIndexOf('.')
                        val parentName = name.substring(0, lastDot)
                        node.references.add("@style/$parentName")
                    }
                }
            }
        }
    }

    private fun findReferences(text: String?, targetSet: MutableSet<String>) {
        if (text == null) return
        REFS_REGEX.findAll(text).forEach { match ->
            targetSet.add(match.value)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(nodeUrl: String) {
            if (nodeUrl in visiting) {
                val cycleStartIndex = path.indexOf(nodeUrl)
                if (cycleStartIndex != -1) {
                    val cyclePath = path.subList(cycleStartIndex, path.size)
                    val cycleSet = cyclePath.toSet()
                    if (cycleSet !in reportedCycles) {
                        reportedCycles.add(cycleSet)
                        val cycleString = (cyclePath + nodeUrl).joinToString(" -> ")
                        val reportNode = cyclePath.mapNotNull { nodes[it] }.firstOrNull()
                        if (reportNode != null) {
                            context.report(
                                ISSUE,
                                reportNode.location,
                                "Cycle detected in resource definitions: $cycleString"
                            )
                        }
                    }
                }
                return
            }
            if (nodeUrl in visited) return

            visiting.add(nodeUrl)
            path.add(nodeUrl)

            val node = nodes[nodeUrl]
            if (node != null) {
                for (ref in node.references) {
                    dfs(ref)
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(nodeUrl)
            visited.add(nodeUrl)
        }

        for (url in nodes.keys) {
            if (url !in visited) {
                dfs(url)
            }
        }

        nodes.clear()
    }
}
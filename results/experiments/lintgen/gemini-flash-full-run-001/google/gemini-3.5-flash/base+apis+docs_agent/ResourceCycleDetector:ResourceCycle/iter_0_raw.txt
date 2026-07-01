package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.CDATASection
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text

class ResourceCycleDetector : Detector(), XmlScanner {

    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        val root = document.documentElement ?: return

        if (folderType == ResourceFolderType.VALUES) {
            var child = root.firstChild
            while (child != null) {
                if (child is Element) {
                    val tagName = child.tagName
                    var type = tagName
                    if (type == "item") {
                        type = child.getAttribute("type") ?: ""
                    }
                    val name = child.getAttribute("name")
                    if (!type.isNullOrEmpty() && !name.isNullOrEmpty()) {
                        val key = "@$type/$name"
                        locations[key] = context.getLocation(child)
                        val refs = dependencies.getOrPut(key) { mutableSetOf() }

                        if (tagName == "style") {
                            val parent = child.getAttribute("parent")
                            if (!parent.isNullOrEmpty()) {
                                val cleanParent = if (parent.startsWith("@")) {
                                    parent
                                } else {
                                    val parentName = parent.substringAfter("android:")
                                    "@style/$parentName"
                                }
                                extractReferences(cleanParent, refs)
                            } else if (name.contains(".")) {
                                val parentName = name.substringBeforeLast(".")
                                if (parentName.isNotEmpty()) {
                                    refs.add("@style/$parentName")
                                }
                            }
                        }

                        findReferences(child, refs)
                    }
                }
                child = child.nextSibling
            }
        } else {
            val fileName = context.file.name.substringBefore('.')
            val key = "@${folderType.name}/$fileName"
            locations[key] = context.getLocation(root)
            val refs = dependencies.getOrPut(key) { mutableSetOf() }
            findReferences(root, refs)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val stack = mutableListOf<String>()

        fun dfs(node: String) {
            if (node in visited) return
            if (node in visiting) {
                val cycleStartIndex = stack.indexOf(node)
                if (cycleStartIndex != -1) {
                    val cycle = stack.subList(cycleStartIndex, stack.size) + node
                    val cycleStr = cycle.joinToString(" -> ")
                    val location = locations[node] ?: locations[cycle.first()]
                    if (location != null) {
                        context.report(
                            ISSUE,
                            location,
                            "Cycle detected in resource definitions: $cycleStr"
                        )
                    }
                    visited.addAll(cycle)
                }
                return
            }

            visiting.add(node)
            stack.add(node)

            dependencies[node]?.forEach { neighbor ->
                dfs(neighbor)
            }

            stack.removeAt(stack.size - 1)
            visiting.remove(node)
            visited.add(node)
        }

        dependencies.keys.sorted().forEach { node ->
            if (node !in visited) {
                dfs(node)
            }
        }

        dependencies.clear()
        locations.clear()
    }

    private fun findReferences(node: Node, refs: MutableSet<String>) {
        if (node is Element) {
            val attributes = node.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                extractReferences(attr.nodeValue, refs)
            }
            var child = node.firstChild
            while (child != null) {
                findReferences(child, refs)
                child = child.nextSibling
            }
        } else if (node is Text || node is CDATASection) {
            extractReferences(node.nodeValue, refs)
        }
    }

    private fun extractReferences(text: String, refs: MutableSet<String>) {
        RESOURCE_PATTERN.findAll(text).forEach { match ->
            val type = match.groups[1]?.value
            val name = match.groups[2]?.value
            if (type != null && name != null) {
                refs.add("@$type/$name")
            }
        }
    }

    companion object {
        private val RESOURCE_PATTERN = Regex("""[?@](?:\w+:)?(\w+)/([\w_.]+)""")

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
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
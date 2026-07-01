package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text

class ResourceCycleDetector : Detector(), XmlScanner {

    private val definedResources = mutableMapOf<String, Location>()
    private val dependencies = mutableMapOf<String, MutableSet<String>>()

    override fun beforeCheckEachProject(context: Context) {
        definedResources.clear()
        dependencies.clear()
    }

    override fun getApplicableFiles(): Int = XmlScanner.ALL

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        val isValues = folderType == ResourceFolderType.VALUES

        if (!isValues) {
            val fileResource = "${folderType.getName()}/${context.file.nameWithoutExtension}"
            definedResources[fileResource] = context.getLocation(document)

            val refs = mutableSetOf<String>()
            collectRefs(document.documentElement, refs)
            if (refs.isNotEmpty()) {
                dependencies.getOrPut(fileResource) { mutableSetOf() }.addAll(refs)
            }
        } else {
            val root = document.documentElement ?: return
            if (root.tagName != "resources") return

            var child = root.firstChild
            while (child != null) {
                if (child is Element) {
                    processValuesElement(context, child)
                }
                child = child.nextSibling
            }
        }
    }

    private fun processValuesElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
        if (name.isNullOrEmpty()) return

        val tagName = element.tagName
        val type = when (tagName) {
            "style" -> "style"
            "string" -> "string"
            "color" -> "color"
            "dimen" -> "dimen"
            "integer" -> "integer"
            "bool" -> "bool"
            "array", "string-array", "integer-array" -> "array"
            "item" -> {
                val t = element.getAttribute("type")
                if (t.isNullOrEmpty()) "item" else t
            }
            else -> tagName
        }

        val resourceKey = "$type/$name"
        definedResources[resourceKey] = context.getLocation(element)

        val refs = mutableSetOf<String>()
        collectRefs(element, refs)

        if (tagName == "style") {
            val parent = element.getAttribute("parent")
            if (!parent.isNullOrEmpty()) {
                refs.add(normalizeStyleParent(parent))
            } else {
                val lastDot = name.lastIndexOf('.')
                if (lastDot != -1) {
                    val parentName = name.substring(0, lastDot)
                    refs.add("style/$parentName")
                }
            }
        }

        if (refs.isNotEmpty()) {
            dependencies.getOrPut(resourceKey) { mutableSetOf() }.addAll(refs)
        }
    }

    private fun collectRefs(node: Node, refs: MutableSet<String>) {
        if (node is Element) {
            val attributes = node.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                refs.addAll(findReferences(attr.nodeValue))
            }
            var child = node.firstChild
            while (child != null) {
                collectRefs(child, refs)
                child = child.nextSibling
            }
        } else if (node is Text) {
            refs.addAll(findReferences(node.nodeValue))
        }
    }

    private val RESOURCE_REF_REGEX = Regex("""[?@](?:[a-zA-Z0-9_.]+?:)?(?:([a-zA-Z0-9_]+)/)?([a-zA-Z0-9_.]+)""")

    private fun findReferences(text: String): List<String> {
        return RESOURCE_REF_REGEX.findAll(text).mapNotNull { match ->
            val type = match.groups[1]?.value ?: if (match.value.startsWith("?")) "attr" else null
            val name = match.groups[2]?.value
            if (name != null) {
                val normType = if (type == "+id") "id" else type
                if (normType != null) "$normType/$name" else null
            } else {
                null
            }
        }.toList()
    }

    private fun normalizeStyleParent(parent: String): String {
        val clean = parent.trim()
        if (clean.startsWith("@style/")) {
            return clean.substring(1)
        }
        if (clean.startsWith("style/")) {
            return clean
        }
        return "style/$clean"
    }

    override fun afterCheckEachProject(context: Context) {
        val state = mutableMapOf<String, Int>() // 0: unvisited, 1: visiting, 2: visited
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<List<String>>()

        fun dfs(node: String) {
            state[node] = 1
            path.add(node)

            val targets = dependencies[node] ?: emptySet()
            for (target in targets) {
                val targetState = state[target] ?: 0
                if (targetState == 1) {
                    val cycleStartIdx = path.indexOf(target)
                    if (cycleStartIdx != -1) {
                        val cycle = path.subList(cycleStartIdx, path.size).toList() + target
                        val normalized = normalizeCycle(cycle)
                        if (reportedCycles.add(normalized)) {
                            val reportNode = cycle.firstOrNull { definedResources.containsKey(it) } ?: cycle.first()
                            val location = definedResources[reportNode]
                            if (location != null) {
                                val cycleString = cycle.joinToString(" -> ")
                                context.report(
                                    ISSUE,
                                    location,
                                    "Cycle detected in resource definitions: $cycleString"
                                )
                            }
                        }
                    }
                } else if (targetState == 0) {
                    dfs(target)
                }
            }

            path.removeAt(path.size - 1)
            state[node] = 2
        }

        for (res in definedResources.keys) {
            if ((state[res] ?: 0) == 0) {
                dfs(res)
            }
        }
    }

    private fun normalizeCycle(cycle: List<String>): List<String> {
        val nodes = cycle.dropLast(1)
        if (nodes.isEmpty()) return cycle
        val minNode = nodes.minOrNull() ?: return cycle
        val minIdx = nodes.indexOf(minNode)
        val rotated = nodes.subList(minIdx, nodes.size) + nodes.subList(0, minIdx)
        return rotated + minNode
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to runtime exceptions.
                """,
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
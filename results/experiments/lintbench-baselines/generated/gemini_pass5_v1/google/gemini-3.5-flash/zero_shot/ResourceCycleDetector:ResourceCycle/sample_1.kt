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
import org.w3c.dom.Node
import java.io.File
import java.util.regex.Pattern

class ResourceCycleDetector : Detector(), XmlScanner {

    private val definitions = mutableMapOf<ResourceId, ResourceDefinition>()

    data class ResourceId(val type: String, val name: String)

    class ResourceDefinition(
        val id: ResourceId,
        val file: File,
        val location: Location,
        val references: MutableSet<ResourceId> = mutableSetOf()
    )

    override fun getApplicableFiles() = Scope.RESOURCE_FILE_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        val root = document.documentElement ?: return

        if (folderType == ResourceFolderType.VALUES) {
            if (root.tagName == "resources") {
                var child = root.firstChild
                while (child != null) {
                    if (child is Element) {
                        var type = child.tagName
                        val name = child.getAttribute("name")
                        if (type == "item") {
                            type = child.getAttribute("type")
                        }
                        if (!type.isNullOrEmpty() && !name.isNullOrEmpty()) {
                            val id = ResourceId(type, name)
                            val definition = ResourceDefinition(id, context.file, context.getLocation(child))

                            val refs = mutableSetOf<ResourceId>()
                            findReferences(child, refs)

                            if (type == "style") {
                                val parent = child.getAttribute("parent")
                                if (!parent.isNullOrEmpty()) {
                                    if (parent.startsWith("@")) {
                                        extractReferencesFromString(parent, refs)
                                    } else {
                                        val cleanParent = if (parent.contains(":")) parent.substringAfter(":") else parent
                                        refs.add(ResourceId("style", cleanParent))
                                    }
                                } else {
                                    if (name.contains(".")) {
                                        val parentName = name.substringBeforeLast(".")
                                        if (parentName.isNotEmpty()) {
                                            refs.add(ResourceId("style", parentName))
                                        }
                                    }
                                }
                            }

                            definition.references.addAll(refs)
                            definitions[id] = definition
                        }
                    }
                    child = child.nextSibling
                }
            }
        } else {
            val type = folderType.getName()
            val name = context.file.nameWithoutExtension
            val id = ResourceId(type, name)
            val definition = ResourceDefinition(id, context.file, context.getLocation(root))

            val refs = mutableSetOf<ResourceId>()
            findReferences(root, refs)
            definition.references.addAll(refs)
            definitions[id] = definition
        }
    }

    private fun findReferences(node: Node, refs: MutableSet<ResourceId>) {
        if (node is Element) {
            val attributes = node.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                extractReferencesFromString(attr.nodeValue, refs)
            }
        }
        var child = node.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                extractReferencesFromString(child.nodeValue, refs)
            } else {
                findReferences(child, refs)
            }
            child = child.nextSibling
        }
    }

    private fun extractReferencesFromString(text: String?, refs: MutableSet<ResourceId>) {
        if (text == null) return
        val matcher = RESOURCE_REF_PATTERN.matcher(text)
        while (matcher.find()) {
            val type = matcher.group(1)
            val name = matcher.group(2)
            if (type != null && name != null) {
                refs.add(ResourceId(type, name))
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        val visited = mutableSetOf<ResourceId>()
        val stack = mutableListOf<ResourceId>()
        val inStack = mutableSetOf<ResourceId>()
        val reportedCycles = mutableSetOf<List<ResourceId>>()

        fun dfs(node: ResourceId) {
            if (node in inStack) {
                val cyclePath = stack.subList(stack.indexOf(node), stack.size) + node
                val normalized = normalizeCycle(cyclePath)
                if (normalized.isNotEmpty() && reportedCycles.add(normalized)) {
                    val firstNode = normalized.first()
                    val definition = definitions[firstNode]
                    if (definition != null) {
                        val pathString = normalized.joinToString(" -> ") { "${it.type}/${it.name}" }
                        val message = "Cycle detected in resource definitions: $pathString"
                        context.report(ISSUE, definition.location, message)
                    }
                }
                return
            }
            if (node in visited) return

            visited.add(node)
            inStack.add(node)
            stack.add(node)

            val definition = definitions[node]
            if (definition != null) {
                for (ref in definition.references) {
                    dfs(ref)
                }
            }

            stack.removeAt(stack.size - 1)
            inStack.remove(node)
        }

        for (id in definitions.keys) {
            if (id !in visited) {
                dfs(id)
            }
        }

        definitions.clear()
    }

    private fun normalizeCycle(path: List<ResourceId>): List<ResourceId> {
        val nodes = path.dropLast(1)
        if (nodes.isEmpty()) return emptyList()
        val minIndex = nodes.indices.minByOrNull { i ->
            val node = nodes[i]
            "${node.type}/${node.name}"
        } ?: 0
        val rotated = nodes.subList(minIndex, nodes.size) + nodes.subList(0, minIndex)
        return rotated + rotated.first()
    }

    companion object {
        private val RESOURCE_REF_PATTERN = Pattern.compile("@(?:[a-zA-Z0-9_.]+:)??([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)")

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
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
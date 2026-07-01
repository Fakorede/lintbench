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

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
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

    private val resources = mutableMapOf<String, ResourceDefinition>()

    override fun beforeCheckProject(context: Context) {
        resources.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        if (folderType != ResourceFolderType.VALUES) {
            return
        }

        val root = document.documentElement ?: return
        if (root.tagName != "resources") return

        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                parseResourceElement(context, child)
            }
        }
    }

    private fun parseResourceElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
        if (name.isNullOrEmpty()) return

        val tagName = element.tagName
        if (tagName == "item") {
            val type = element.getAttribute("type")
            if (!type.isNullOrEmpty()) {
                val fqName = "$type/$name"
                val definition = resources.getOrPut(fqName) {
                    ResourceDefinition(type, name)
                }
                if (definition.location == null) {
                    definition.location = context.getLocation(element)
                }
                parseReferences(element, definition)
            }
            return
        }

        val fqName = "$tagName/$name"
        val definition = resources.getOrPut(fqName) {
            ResourceDefinition(tagName, name)
        }
        if (definition.location == null) {
            definition.location = context.getLocation(element)
        }

        if (tagName == "style") {
            parseStyleReferences(element, definition)
        } else {
            parseReferences(element, definition)
        }
    }

    private fun parseStyleReferences(element: Element, definition: ResourceDefinition) {
        val name = definition.name
        val parentAttr = element.getAttribute("parent")
        if (!parentAttr.isNullOrEmpty()) {
            val parentRef = if (parentAttr.startsWith("@style/")) {
                parentAttr.substring(1)
            } else if (parentAttr.startsWith("@")) {
                parentAttr.substring(1)
            } else {
                "style/$parentAttr"
            }
            definition.references.add(parentRef)
        } else {
            val lastDot = name.lastIndexOf('.')
            if (lastDot > 0) {
                val parentName = name.substring(0, lastDot)
                definition.references.add("style/$parentName")
            }
        }
    }

    private fun parseReferences(element: Element, definition: ResourceDefinition) {
        val text = element.textContent?.trim() ?: ""
        if (text.startsWith("@") && !text.startsWith("@+")) {
            val ref = text.substring(1)
            definition.references.add(ref)
        }
    }

    override fun afterCheckProject(context: Context) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(current: String) {
            if (current in visiting) {
                val cycleStartIndex = path.indexOf(current)
                if (cycleStartIndex != -1) {
                    val cycle = path.subList(cycleStartIndex, path.size) + current
                    val cycleParticipants = cycle.dropLast(1).toSet()
                    if (reportedCycles.add(cycleParticipants)) {
                        val cycleString = cycle.joinToString(" -> ")
                        val startNode = resources[current]
                        val location = startNode?.location ?: Location.create(context.file)
                        context.report(
                            ISSUE,
                            location,
                            "Cycle in resource definitions: $cycleString"
                        )
                    }
                }
                return
            }
            if (current in visited) return

            visiting.add(current)
            path.add(current)

            val node = resources[current]
            if (node != null) {
                for (ref in node.references) {
                    dfs(ref)
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(current)
            visited.add(current)
        }

        for (resKey in resources.keys) {
            if (resKey !in visited) {
                dfs(resKey)
            }
        }
    }

    private data class ResourceDefinition(
        val type: String,
        val name: String,
        val references: MutableSet<String> = mutableSetOf(),
        var location: Location? = null
    )
}
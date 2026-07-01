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

class ResourceCycleDetector : Detector(), XmlScanner {

    private class Edge(val target: String, val location: Location)

    private val dependencies = mutableMapOf<String, MutableList<Edge>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun beforeCheckProject(context: Context) {
        dependencies.clear()
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        val folderType = context.resourceFolderType ?: return

        if (folderType == ResourceFolderType.VALUES) {
            if (root.tagName == "resources") {
                var child = root.firstChild
                while (child != null) {
                    if (child is Element) {
                        processValueResource(context, child)
                    }
                    child = child.nextSibling
                }
            }
        } else {
            val resId = "${folderType.getName()}/${context.file.nameWithoutExtension}"
            recordReferences(context, root, resId, isRoot = false)
        }
    }

    private fun processValueResource(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val name = element.getAttribute("name")
        if (name.isNullOrEmpty()) return

        val type = if (tagName == "item") {
            element.getAttribute("type") ?: ""
        } else if (tagName == "string-array" || tagName == "integer-array" || tagName == "array") {
            "array"
        } else {
            tagName
        }
        if (type.isEmpty()) return

        val resId = "$type/$name"
        recordReferences(context, element, resId, isRoot = true)
    }

    private fun recordReferences(context: XmlContext, element: Element, resId: String, isRoot: Boolean) {
        if (isRoot && element.tagName == "style") {
            val parentAttr = element.getAttributeNode("parent")
            if (parentAttr != null) {
                val parentVal = parentAttr.value
                if (parentVal.isNotEmpty()) {
                    val target = if (parentVal.startsWith("@")) {
                        extractReferences(parentVal).firstOrNull()
                    } else if (parentVal.startsWith("android:")) {
                        null
                    } else {
                        "style/$parentVal"
                    }
                    if (target != null) {
                        val loc = context.getValueLocation(parentAttr)
                        dependencies.getOrPut(resId) { mutableListOf() }.add(Edge(target, loc))
                    }
                }
            } else {
                val name = element.getAttribute("name")
                if (name != null && name.contains('.')) {
                    val parentName = name.substringBeforeLast('.')
                    val target = "style/$parentName"
                    val loc = context.getNameLocation(element)
                    dependencies.getOrPut(resId) { mutableListOf() }.add(Edge(target, loc))
                }
            }
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            if (isRoot && element.tagName == "style" && attr.name == "parent") {
                continue
            }
            val value = attr.value ?: continue
            val refs = extractReferences(value)
            if (refs.isNotEmpty()) {
                val loc = context.getValueLocation(attr)
                for (ref in refs) {
                    dependencies.getOrPut(resId) { mutableListOf() }.add(Edge(ref, loc))
                }
            }
        }

        val text = element.textContent
        if (!text.isNullOrEmpty() && (text.contains('@') || text.contains('?'))) {
            var hasChildElements = false
            var child = element.firstChild
            while (child != null) {
                if (child is Element) {
                    hasChildElements = true
                    break
                }
                child = child.nextSibling
            }
            if (!hasChildElements) {
                val refs = extractReferences(text)
                if (refs.isNotEmpty()) {
                    val loc = context.getLocation(element)
                    for (ref in refs) {
                        dependencies.getOrPut(resId) { mutableListOf() }.add(Edge(ref, loc))
                    }
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                recordReferences(context, child, resId, isRoot = false)
            }
            child = child.nextSibling
        }
    }

    override fun afterCheckProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            visiting.add(node)
            path.add(node)

            val edges = dependencies[node] ?: emptyList()
            for (edge in edges) {
                val target = edge.target
                if (target in visiting) {
                    val (nodeType, nodeName) = splitResource(node)
                    val isSelf = (node == target)
                    val message = if (isSelf) {
                        if (nodeType == "style") {
                            "Style $nodeName should not extend itself"
                        } else {
                            "${nodeType.capitalize()} $nodeName should not reference itself"
                        }
                    } else {
                        if (nodeType == "style") {
                            "Style $nodeName has a loop in its parent chain"
                        } else {
                            "${nodeType.capitalize()} $nodeName has a loop in its reference chain"
                        }
                    }
                    context.report(
                        ISSUE,
                        edge.location,
                        message
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

    private fun splitResource(res: String): Pair<String, String> {
        val index = res.indexOf('/')
        if (index != -1) {
            return Pair(res.substring(0, index), res.substring(index + 1))
        }
        return Pair("", res)
    }

    private fun String.capitalize(): String {
        if (isEmpty()) return this
        val first = this[0]
        return if (first.isLowerCase()) Character.toUpperCase(first) + substring(1) else this
    }

    private fun extractReferences(text: String): List<String> {
        return RESOURCE_REF_PATTERN.findAll(text).mapNotNull { match ->
            val prefix = match.groupValues[1]
            if (prefix == "android") {
                null
            } else {
                val type = match.groupValues[2].ifEmpty {
                    if (match.value.startsWith("?")) "attr" else ""
                }
                val name = match.groupValues[3]
                if (type.isNotEmpty() && name.isNotEmpty()) {
                    "$type/$name"
                } else {
                    null
                }
            }
        }.toList()
    }

    companion object {
        private val RESOURCE_REF_PATTERN = Regex("""[?@](?:([a-zA-Z0-9_.]+):)?(?:([a-zA-Z0-9_.]+)/)?([a-zA-Z0-9_.]+)""")

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
}
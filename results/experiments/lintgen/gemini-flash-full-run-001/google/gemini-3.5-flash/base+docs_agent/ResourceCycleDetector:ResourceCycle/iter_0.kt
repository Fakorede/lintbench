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
import org.w3c.dom.Text

class ResourceCycleDetector : Detector(), XmlScanner {

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

    private class ResourceNode(
        val url: String,
        val location: Location,
        val dependencies: MutableSet<String> = mutableSetOf()
    )

    private val resources = mutableMapOf<String, ResourceNode>()

    override fun beforeCheckEachProject(context: Context) {
        resources.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        if (folderType != ResourceFolderType.VALUES) return

        val root = document.documentElement ?: return
        if (root.tagName != "resources") return

        var child = root.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.getAttribute("name")
                if (name.isNotEmpty()) {
                    val type = if (child.tagName == "item") {
                        child.getAttribute("type")
                    } else {
                        child.tagName
                    }
                    if (type.isNotEmpty()) {
                        val url = "@$type/$name"
                        val location = context.getLocation(child)
                        
                        val node = resources.getOrPut(url) {
                            ResourceNode(url, location)
                        }
                        
                        val dependencies = mutableSetOf<String>()
                        
                        if (type == "style") {
                            val parentAttr = child.getAttribute("parent")
                            if (parentAttr.isNotEmpty()) {
                                val parentRef = if (parentAttr.startsWith("@") || parentAttr.startsWith("?")) {
                                    parentAttr
                                } else {
                                    "@style/$parentAttr"
                                }
                                val normalized = normalizeReference(parentRef)
                                if (normalized != null) {
                                    dependencies.add(normalized)
                                }
                            } else if (name.contains('.')) {
                                val lastDot = name.lastIndexOf('.')
                                val parentName = name.substring(0, lastDot)
                                dependencies.add("@style/$parentName")
                            }
                        }
                        
                        extractDepsFromNode(child, dependencies)
                        node.dependencies.addAll(dependencies)
                    }
                }
            }
            child = child.nextSibling
        }
    }

    private fun extractDepsFromNode(node: Node, dependencies: MutableSet<String>) {
        if (node is Element) {
            val attributes = node.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                val value = attr.nodeValue
                if (attr.nodeName != "name" && attr.nodeName != "parent") {
                    extractReferences(value, dependencies)
                }
            }
            var child = node.firstChild
            while (child != null) {
                extractDepsFromNode(child, dependencies)
                child = child.nextSibling
            }
        } else if (node is Text) {
            extractReferences(node.nodeValue, dependencies)
        }
    }

    private fun extractReferences(text: String, dependencies: MutableSet<String>) {
        val matches = Regex("""[@?][a-zA-Z0-9_.:/]+""").findAll(text)
        for (match in matches) {
            val ref = match.value
            val normalized = normalizeReference(ref)
            if (normalized != null) {
                dependencies.add(normalized)
            }
        }
    }

    private fun normalizeReference(ref: String): String? {
        if (ref.contains("android:")) return null
        val clean = ref.trim()
        if (clean.startsWith("@") || clean.startsWith("?")) {
            val body = clean.substring(1)
            if (body.contains("/")) {
                val parts = body.split("/")
                if (parts.size == 2) {
                    val type = parts[0]
                    val name = parts[1]
                    if (type.isNotEmpty() && name.isNotEmpty()) {
                        return "@$type/$name"
                    }
                }
            } else {
                if (clean.startsWith("?")) {
                    return "@attr/$body"
                }
            }
        }
        return null
    }

    override fun afterCheckEachProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = LinkedHashSet<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(url: String) {
            if (visiting.contains(url)) {
                val path = visiting.toList()
                val cycleStartIndex = path.indexOf(url)
                if (cycleStartIndex != -1) {
                    val cyclePath = path.subList(cycleStartIndex, path.size) + url
                    val cycleSet = cyclePath.toSet()
                    if (reportedCycles.add(cycleSet)) {
                        val cycleString = cyclePath.joinToString(" -> ")
                        val node = resources[url]
                        if (node != null) {
                            context.report(
                                ISSUE,
                                node.location,
                                "Cycle detected in resource definitions: $cycleString"
                            )
                        }
                    }
                }
                return
            }
            if (visited.contains(url)) {
                return
            }

            visiting.add(url)
            val node = resources[url]
            if (node != null) {
                for (dep in node.dependencies) {
                    dfs(dep)
                }
            }
            visiting.remove(url)
            visited.add(url)
        }

        for (url in resources.keys) {
            if (!visited.contains(url)) {
                dfs(url)
            }
        }
    }
}
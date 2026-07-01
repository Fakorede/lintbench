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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

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
    }

    private class ResourceNode(
        val url: String,
        val location: Location,
        val dependencies: MutableSet<String> = mutableSetOf()
    )

    private val resources = mutableMapOf<String, ResourceNode>()

    override fun beforeCheckRootProject(context: Context) {
        resources.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeName != "resources") {
            return
        }
        val name = element.getAttribute("name")
        if (name.isEmpty()) {
            return
        }
        val tag = element.tagName
        val type = if (tag == "item") {
            val t = element.getAttribute("type")
            if (t.isEmpty()) return
            t
        } else {
            tag
        }

        val url = "@$type/$name"
        val location = context.getLocation(element)
        val node = ResourceNode(url, location)

        if (type == "style") {
            if (element.hasAttribute("parent")) {
                val parentVal = element.getAttribute("parent")
                if (parentVal.isNotEmpty()) {
                    node.dependencies.add(getStyleUrl(parentVal))
                }
            } else {
                val lastDot = name.lastIndexOf('.')
                if (lastDot != -1) {
                    val parentName = name.substring(0, lastDot)
                    node.dependencies.add("@style/$parentName")
                }
            }
        }

        findReferences(element, node.dependencies)
        resources[url] = node
    }

    override fun afterCheckRootProject(context: Context) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(url: String) {
            visiting.add(url)
            path.add(url)

            val node = resources[url]
            if (node != null) {
                for (dep in node.dependencies) {
                    if (visiting.contains(dep)) {
                        val cycleIndex = path.indexOf(dep)
                        if (cycleIndex != -1) {
                            val cyclePath = path.subList(cycleIndex, path.size) + dep
                            val cycleStr = cyclePath.joinToString(" -> ")
                            val message = "Cycle in resource definitions: $cycleStr"
                            context.report(
                                ISSUE,
                                node.location,
                                message
                            )
                        }
                    } else if (!visited.contains(dep) && resources.containsKey(dep)) {
                        dfs(dep)
                    }
                }
            }

            path.removeAt(path.size - 1)
            visiting.remove(url)
            visited.add(url)
        }

        for (url in resources.keys) {
            if (!visited.contains(url)) {
                dfs(url)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    private fun getStyleUrl(parent: String): String {
        if (parent.startsWith("@style/")) {
            return parent
        }
        if (parent.startsWith("@android:style/")) {
            return parent
        }
        if (parent.startsWith("android:")) {
            return "@style/" + parent
        }
        return "@style/" + parent
    }

    private fun findReferences(node: org.w3c.dom.Node, refs: MutableSet<String>) {
        if (node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
            val text = node.nodeValue.trim()
            if (text.startsWith("@") && !text.startsWith("@android:") && text.contains("/")) {
                refs.add(text)
            }
        } else if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
            var child = node.firstChild
            while (child != null) {
                findReferences(child, refs)
                child = child.nextSibling
            }
        }
    }
}
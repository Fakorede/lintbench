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
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    private class ResourceInfo(
        val key: String,
        val location: Location
    ) {
        val dependencies = mutableSetOf<String>()
    }

    private val definedResources = mutableMapOf<String, ResourceInfo>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun beforeCheckEachProject(context: Context) {
        definedResources.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        val root = document.documentElement ?: return

        if (folderType == ResourceFolderType.VALUES) {
            var child = root.firstChild
            while (child != null) {
                if (child is Element) {
                    processValueElement(context, child)
                }
                child = child.nextSibling
            }
        } else {
            val type = folderType.getName()
            val name = context.file.name.substringBefore('.')
            val key = "@$type/$name"
            val resInfo = ResourceInfo(key, context.getLocation(root))
            definedResources[key] = resInfo
            scanForReferences(root, resInfo)
        }
    }

    private fun processValueElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "resources") return

        var type = tagName
        if (type == "item") {
            type = element.getAttribute("type") ?: ""
        }
        val name = element.getAttribute("name")
        if (type.isEmpty() || name.isEmpty()) return

        val key = "@$type/$name"
        val resInfo = ResourceInfo(key, context.getLocation(element))
        definedResources[key] = resInfo

        if (type == "style") {
            val parent = element.getAttribute("parent")
            if (parent.isNotEmpty()) {
                val parentKey = if (parent.startsWith("@")) {
                    cleanReference(parent)
                } else if (parent.contains("android:")) {
                    null
                } else {
                    "@style/$parent"
                }
                if (parentKey != null) {
                    resInfo.dependencies.add(parentKey)
                }
            } else if (name.contains('.')) {
                val parentName = name.substringBeforeLast('.')
                if (parentName.isNotEmpty()) {
                    resInfo.dependencies.add("@style/$parentName")
                }
            }
        }

        scanForReferences(element, resInfo)
    }

    private fun scanForReferences(element: Element, resInfo: ResourceInfo) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.localName
            if (name == "name" && element.parentNode?.nodeName == "resources") {
                continue
            }
            if (name == "parent" && element.tagName == "style") {
                continue
            }
            val value = attr.nodeValue ?: continue
            extractAndAddReferences(value, resInfo)
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                scanForReferences(child, resInfo)
            } else if (child.nodeType == org.w3c.dom.Node.TEXT_NODE || child.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue ?: ""
                extractAndAddReferences(text, resInfo)
            }
            child = child.nextSibling
        }
    }

    private fun extractAndAddReferences(text: String, resInfo: ResourceInfo) {
        val matches = RESOURCE_PATTERN.findAll(text)
        for (match in matches) {
            val ref = cleanReference(match.value)
            if (ref != null && ref != resInfo.key) {
                resInfo.dependencies.add(ref)
            }
        }
    }

    private fun cleanReference(ref: String): String? {
        if (ref.contains("android:")) return null
        val prefix = if (ref.startsWith('@')) "@" else if (ref.startsWith('?')) "?" else return null
        val index = ref.indexOf('/')
        if (index == -1) {
            val name = ref.substring(1)
            val type = if (prefix == "?") "attr" else return null
            return "$prefix$type/$name"
        }
        val typePart = ref.substring(0, index)
        val namePart = ref.substring(index + 1)
        if (typePart.length <= 1) return null
        val type = typePart.substring(1)
        return "$prefix$type/$namePart"
    }

    override fun afterCheckEachProject(context: Context) {
        val visited = mutableSetOf<String>()
        val stack = LinkedHashSet<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(current: String) {
            if (current in stack) {
                val cycleList = stack.toList()
                val cycleStartIndex = cycleList.indexOf(current)
                val cycle = cycleList.subList(cycleStartIndex, cycleList.size)
                val cycleSet = cycle.toSet()
                if (reportedCycles.add(cycleSet)) {
                    val cyclePath = (cycle + current).joinToString(" -> ")
                    val firstNode = definedResources[current]
                    if (firstNode != null) {
                        context.report(
                            ISSUE,
                            firstNode.location,
                            "Cycle in resource definitions: $cyclePath"
                        )
                    }
                }
                return
            }

            if (current in visited) return

            visited.add(current)
            val info = definedResources[current] ?: return

            stack.add(current)
            for (dep in info.dependencies) {
                dfs(dep)
            }
            stack.remove(current)
        }

        for (key in definedResources.keys) {
            if (key !notIn visited) {
                dfs(key)
            }
        }

        definedResources.clear()
    }

    private infix fun String.notIn(set: Set<String>): Boolean = !set.contains(this)

    companion object {
        private val RESOURCE_PATTERN = Regex("""[@?](?:[a-zA-Z0-9_.]+:)?[a-zA-Z0-9_]+/[a-zA-Z0-9_.]+""")

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
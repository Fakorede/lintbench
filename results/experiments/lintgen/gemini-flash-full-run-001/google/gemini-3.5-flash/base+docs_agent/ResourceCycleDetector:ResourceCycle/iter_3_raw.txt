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
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }

    private class Dependency(
        val target: String,
        val location: Location
    )

    private class ResourceInfo(
        val url: String,
        val type: String,
        val name: String,
        val definitionLocation: Location,
        val dependencies: MutableList<Dependency> = mutableListOf()
    )

    private val resources = mutableMapOf<String, ResourceInfo>()
    private val referencePattern = Regex("""@(?!(?:android|google):)\+?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)""")

    override fun beforeCheckProject(context: Context) {
        resources.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType 
            ?: ResourceFolderType.getFolderType(context.file.parentFile?.name?.substringBefore('-') ?: "")
            ?: run {
                val rootTag = document.documentElement?.tagName
                if (rootTag == "resources") {
                    ResourceFolderType.VALUES
                } else if (rootTag == "layout" || rootTag == "merge" || rootTag?.endsWith("Layout") == true) {
                    ResourceFolderType.LAYOUT
                } else {
                    ResourceFolderType.DRAWABLE
                }
            }

        if (folderType == ResourceFolderType.VALUES) {
            val root = document.documentElement ?: return
            if (root.tagName != "resources") return

            var child = root.firstChild
            while (child != null) {
                if (child is Element) {
                    val tagName = child.tagName
                    if (tagName == "declare-styleable" || tagName == "attr") {
                        child = child.nextSibling
                        continue
                    }
                    val name = child.getAttribute("name")
                    if (name.isNotEmpty()) {
                        val type = if (tagName == "item") {
                            child.getAttribute("type")
                        } else if (tagName.endsWith("-array")) {
                            "array"
                        } else {
                            tagName
                        }
                        if (type.isNotEmpty()) {
                            val url = "@$type/$name"
                            val definitionLocation = context.getLocation(child)
                            val resourceInfo = resources.getOrPut(url) {
                                ResourceInfo(url, type, name, definitionLocation)
                            }
                            
                            if (type == "style") {
                                val parentAttr = child.getAttributeNode("parent")
                                if (parentAttr != null) {
                                    val parentVal = parentAttr.value.trim()
                                    if (parentVal.isNotEmpty() && !parentVal.startsWith("?") && !parentVal.contains("android:")) {
                                        val parentName = if (parentVal.startsWith("@style/")) {
                                            parentVal.substring("@style/".length)
                                        } else if (parentVal.startsWith("style/")) {
                                            parentVal.substring("style/".length)
                                        } else {
                                            parentVal
                                        }
                                        resourceInfo.dependencies.add(Dependency("@style/$parentName", context.getLocation(parentAttr)))
                                    }
                                } else if (name.contains('.')) {
                                    val parentName = name.substring(0, name.lastIndexOf('.'))
                                    resourceInfo.dependencies.add(Dependency("@style/$parentName", context.getLocation(child)))
                                }
                            }
                            extractReferences(child, context, resourceInfo.dependencies)
                        }
                    }
                }
                child = child.nextSibling
            }
        } else {
            val type = folderType.getName()
            val name = context.file.nameWithoutExtension
            val url = "@$type/$name"
            val definitionLocation = context.getLocation(document.documentElement ?: return)
            val resourceInfo = resources.getOrPut(url) {
                ResourceInfo(url, type, name, definitionLocation)
            }
            extractReferences(document.documentElement, context, resourceInfo.dependencies)
        }
    }

    private fun extractReferences(
        element: Element,
        context: XmlContext,
        dependencies: MutableList<Dependency>
    ) {
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as org.w3c.dom.Attr
                val attrName = attr.name
                if (attrName == "name") continue
                if (element.tagName == "style" && attrName == "parent") continue
                
                val value = attr.value
                val refs = findAllReferences(value)
                for (ref in refs) {
                    dependencies.add(Dependency(ref, context.getLocation(attr)))
                }
            }
        }
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                val value = child.nodeValue
                if (value != null) {
                    val refs = findAllReferences(value)
                    for (ref in refs) {
                        dependencies.add(Dependency(ref, context.getLocation(child)))
                    }
                }
            } else if (child is Element) {
                extractReferences(child, context, dependencies)
            }
            child = child.nextSibling
        }
    }

    private fun findAllReferences(value: String): List<String> {
        val results = mutableListOf<String>()
        for (match in referencePattern.findAll(value)) {
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            results.add("@$type/$name")
        }
        return results
    }

    override fun afterCheckProject(context: Context) {
        val visited = mutableSetOf<String>()
        val visiting = mutableMapOf<String, Dependency?>()
        val path = mutableListOf<ResourceInfo>()

        fun dfs(resource: ResourceInfo, incoming: Dependency?) {
            val url = resource.url
            if (visiting.containsKey(url)) {
                val isSelf = (incoming != null && url == path.lastOrNull()?.url)
                val typeStr = resource.type.substring(0, 1).uppercase() + resource.type.substring(1)
                val message = if (isSelf) {
                    "$typeStr ${resource.name} should not reference itself"
                } else {
                    "$typeStr ${resource.name} has a cycle"
                }
                val location = incoming?.location ?: resource.definitionLocation
                context.report(ISSUE, location, message)
                return
            }
            if (visited.contains(url)) {
                return
            }

            visiting[url] = incoming
            path.add(resource)

            for (dep in resource.dependencies) {
                val targetResource = resources[dep.target]
                if (targetResource != null) {
                    dfs(targetResource, dep)
                }
            }

            path.removeAt(path.lastIndex)
            visiting.remove(url)
            visited.add(url)
        }

        for (resource in resources.values) {
            if (!visited.contains(resource.url)) {
                dfs(resource, null)
            }
        }
    }
}
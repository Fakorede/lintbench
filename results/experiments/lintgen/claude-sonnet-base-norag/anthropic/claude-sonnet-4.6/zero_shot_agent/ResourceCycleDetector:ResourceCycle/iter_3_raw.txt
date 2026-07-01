/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
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

/**
 * Checks for cycles in resource definitions (e.g. a style that extends itself,
 * or a color that references itself).
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    // Maps resource type -> (resource name -> list of referenced resource names of same type)
    private val graph = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

    // Location map for reporting: "type:name" -> Location
    private val locationMap = mutableMapOf<String, Location>()

    // Store XmlContext per location key for reporting
    private val contextMap = mutableMapOf<String, XmlContext>()
    private val elementMap = mutableMapOf<String, Element>()

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

        private const val KEY_STYLE = "style"
        private const val KEY_COLOR = "color"
        private const val KEY_DRAWABLE = "drawable"
        private const val KEY_LAYOUT = "layout"
        private const val KEY_FONT = "font"
        private const val KEY_STRING = "string"

        private const val COLOR_PREFIX = "@color/"
        private const val DRAWABLE_PREFIX = "@drawable/"
        private const val LAYOUT_PREFIX = "@layout/"
        private const val STYLE_PREFIX = "@style/"
        private const val FONT_PREFIX = "@font/"
        private const val STRING_PREFIX = "@string/"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
                folderType == ResourceFolderType.COLOR ||
                folderType == ResourceFolderType.DRAWABLE ||
                folderType == ResourceFolderType.LAYOUT ||
                folderType == ResourceFolderType.FONT
    }

    private fun addEdge(
        type: String,
        from: String,
        to: String,
        context: XmlContext,
        element: Element
    ) {
        val typeGraph = graph.getOrPut(type) { mutableMapOf() }
        typeGraph.getOrPut(from) { mutableListOf() }.add(to)
        val key = "$type:$from"
        if (!locationMap.containsKey(key)) {
            locationMap[key] = context.getLocation(element)
            contextMap[key] = context
            elementMap[key] = element
        }
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val folderType = context.resourceFolderType ?: return

        when (folderType) {
            ResourceFolderType.VALUES -> visitValuesDocument(context, root)
            ResourceFolderType.COLOR -> visitColorDocument(context, root)
            ResourceFolderType.DRAWABLE -> visitDrawableDocument(context, root)
            ResourceFolderType.LAYOUT -> visitLayoutDocument(context, root)
            ResourceFolderType.FONT -> visitFontDocument(context, root)
            else -> { /* not applicable */ }
        }
    }

    private fun visitValuesDocument(context: XmlContext, root: Element) {
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue
            when (node.tagName) {
                TAG_STYLE -> handleStyleElement(context, node)
                KEY_COLOR -> handleColorValueElement(context, node)
                KEY_DRAWABLE -> handleDrawableValueElement(context, node)
                KEY_STRING -> handleStringValueElement(context, node)
                TAG_ITEM -> {
                    val type = node.getAttribute(ATTR_TYPE)
                    when (type) {
                        KEY_STYLE -> handleStyleElement(context, node)
                        KEY_COLOR -> handleColorValueElement(context, node)
                        KEY_DRAWABLE -> handleDrawableValueElement(context, node)
                        KEY_LAYOUT -> handleLayoutValueElement(context, node)
                        KEY_FONT -> handleFontValueElement(context, node)
                        KEY_STRING -> handleStringValueElement(context, node)
                    }
                }
            }
        }
    }

    private fun handleStyleElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val normalizedName = name.replace('.', '_')

        var parent = element.getAttribute(ATTR_PARENT)
        if (parent.isNotEmpty()) {
            // Skip android framework parents
            if (parent.startsWith("@android:style/") || parent.startsWith("android:")) {
                return
            }
            if (parent.startsWith(STYLE_PREFIX)) {
                parent = parent.substring(STYLE_PREFIX.length)
            }
            val normalizedParent = parent.replace('.', '_')
            if (normalizedParent.isNotEmpty()) {
                addEdge(KEY_STYLE, normalizedName, normalizedParent, context, element)
            }
        } else {
            // Check for implicit parent via dot notation: "ParentStyle.ChildStyle"
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = name.substring(0, dotIndex).replace('.', '_')
                addEdge(KEY_STYLE, normalizedName, implicitParent, context, element)
            }
        }
    }

    private fun handleColorValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(COLOR_PREFIX)) {
            val refName = text.substring(COLOR_PREFIX.length).trim()
            if (refName.isNotEmpty()) {
                addEdge(KEY_COLOR, name, refName, context, element)
            }
        }
    }

    private fun handleDrawableValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(DRAWABLE_PREFIX)) {
            val refName = text.substring(DRAWABLE_PREFIX.length).trim()
            if (refName.isNotEmpty()) {
                addEdge(KEY_DRAWABLE, name, refName, context, element)
            }
        }
    }

    private fun handleLayoutValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(LAYOUT_PREFIX)) {
            val refName = text.substring(LAYOUT_PREFIX.length).trim()
            if (refName.isNotEmpty()) {
                addEdge(KEY_LAYOUT, name, refName, context, element)
            }
        }
    }

    private fun handleFontValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(FONT_PREFIX)) {
            val refName = text.substring(FONT_PREFIX.length).trim()
            if (refName.isNotEmpty()) {
                addEdge(KEY_FONT, name, refName, context, element)
            }
        }
    }

    private fun handleStringValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(STRING_PREFIX)) {
            val refName = text.substring(STRING_PREFIX.length).trim()
            if (refName.isNotEmpty()) {
                addEdge(KEY_STRING, name, refName, context, element)
            }
        }
    }

    private fun visitColorDocument(context: XmlContext, root: Element) {
        val fileName = context.file.nameWithoutExtension
        collectRefsFromElementRecursive(context, root, fileName, KEY_COLOR, COLOR_PREFIX)
    }

    private fun visitDrawableDocument(context: XmlContext, root: Element) {
        val fileName = context.file.nameWithoutExtension
        collectRefsFromElementRecursive(context, root, fileName, KEY_DRAWABLE, DRAWABLE_PREFIX)
    }

    private fun visitLayoutDocument(context: XmlContext, root: Element) {
        val fileName = context.file.nameWithoutExtension
        collectLayoutRefsRecursive(context, root, fileName)
    }

    private fun visitFontDocument(context: XmlContext, root: Element) {
        val fileName = context.file.nameWithoutExtension
        collectRefsFromElementRecursive(context, root, fileName, KEY_FONT, FONT_PREFIX)
    }

    private fun collectRefsFromElementRecursive(
        context: XmlContext,
        element: Element,
        fileName: String,
        type: String,
        prefix: String
    ) {
        // Check attributes
        val attrs = element.attributes
        if (attrs != null) {
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                val value = attr.nodeValue ?: continue
                if (value.startsWith(prefix)) {
                    val refName = value.substring(prefix.length).trim()
                    if (refName.isNotEmpty()) {
                        addEdge(type, fileName, refName, context, element)
                    }
                }
            }
        }
        // Check text content (only for leaf nodes)
        val childNodes = element.childNodes
        var hasElementChildren = false
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i) is Element) {
                hasElementChildren = true
                break
            }
        }
        if (!hasElementChildren) {
            val text = element.textContent?.trim()
            if (text != null && text.startsWith(prefix)) {
                val refName = text.substring(prefix.length).trim()
                if (refName.isNotEmpty()) {
                    addEdge(type, fileName, refName, context, element)
                }
            }
        }

        // Recurse into children
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                collectRefsFromElementRecursive(context, child, fileName, type, prefix)
            }
        }
    }

    private fun collectLayoutRefsRecursive(context: XmlContext, element: Element, fileName: String) {
        val attrs = element.attributes
        if (attrs != null) {
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                val value = attr.nodeValue ?: continue
                if (value.startsWith(LAYOUT_PREFIX)) {
                    val refName = value.substring(LAYOUT_PREFIX.length).trim()
                    if (refName.isNotEmpty()) {
                        addEdge(KEY_LAYOUT, fileName, refName, context, element)
                    }
                }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                collectLayoutRefsRecursive(context, child, fileName)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for ((type, typeGraph) in graph) {
            detectCycles(type, typeGraph)
        }
    }

    private fun detectCycles(
        resourceType: String,
        typeGraph: Map<String, List<String>>
    ) {
        // Use DFS to detect cycles
        // States: 0 = unvisited, 1 = in progress, 2 = done
        val state = mutableMapOf<String, Int>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>) {
            state[node] = 1
            path.add(node)

            val neighbors = typeGraph[node] ?: emptyList()
            for (neighbor in neighbors) {
                val neighborState = state[neighbor] ?: 0
                if (neighborState == 1) {
                    // Found a cycle - neighbor is in current path
                    val cycleStartIndex = path.indexOf(neighbor)
                    if (cycleStartIndex >= 0) {
                        val cycle = path.subList(cycleStartIndex, path.size)
                        // Report on each node in the cycle that has a location
                        for (nodeName in cycle) {
                            if (nodeName !in reported) {
                                reported.add(nodeName)
                                val key = "$resourceType:$nodeName"
                                val xmlContext = contextMap[key]
                                val element = elementMap[key]
                                if (xmlContext != null && element != null) {
                                    val message = buildCycleMessage(resourceType, cycle, nodeName, neighbor)
                                    xmlContext.report(
                                        ISSUE,
                                        element,
                                        xmlContext.getLocation(element),
                                        message
                                    )
                                }
                            }
                        }
                    }
                } else if (neighborState == 0) {
                    dfs(neighbor, path)
                }
            }

            path.removeAt(path.size - 1)
            state[node] = 2
        }

        for (node in typeGraph.keys) {
            if ((state[node] ?: 0) == 0) {
                dfs(node, mutableListOf())
            }
        }
    }

    private fun buildCycleMessage(
        resourceType: String,
        cycle: List<String>,
        currentNode: String,
        cycleBackTo: String
    ): String {
        val prefix = when (resourceType) {
            KEY_STYLE -> "@style/"
            KEY_COLOR -> "@color/"
            KEY_DRAWABLE -> "@drawable/"
            KEY_LAYOUT -> "@layout/"
            KEY_FONT -> "@font/"
            KEY_STRING -> "@string/"
            else -> "@$resourceType/"
        }

        // Check if it's a self-reference
        if (cycle.size == 1 || (cycle.size >= 1 && currentNode == cycleBackTo)) {
            return "$prefix$currentNode should not reference itself"
        }

        // Build cycle description starting from currentNode
        val startIndex = cycle.indexOf(currentNode)
        val orderedCycle = if (startIndex >= 0) {
            cycle.subList(startIndex, cycle.size) + cycle.subList(0, startIndex)
        } else {
            cycle
        }

        // Check if it's a direct self-reference (node references itself)
        val neighbors = graph[resourceType]?.get(currentNode) ?: emptyList()
        if (neighbors.contains(currentNode)) {
            return "$prefix$currentNode should not reference itself"
        }

        val sb = StringBuilder()
        sb.append("$prefix$currentNode => ")
        for (i in 1 until orderedCycle.size) {
            sb.append("$prefix${orderedCycle[i]}")
            if (i < orderedCycle.size - 1) {
                sb.append(" => ")
            }
        }
        sb.append(" => $prefix${orderedCycle[0]}")

        return sb.toString()
    }
}
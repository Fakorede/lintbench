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

import com.android.SdkConstants.ANDROID_NS_NAME_PREFIX
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.COLOR_RESOURCE_PREFIX
import com.android.SdkConstants.DRAWABLE_PREFIX
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Checks for cycles in resource definitions (e.g. style A extends style B which extends style A).
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    /**
     * Map from resource type to a map of resource name -> referenced resource name.
     * For styles, the reference is the parent style name.
     * For colors/drawables, the reference is the aliased resource name.
     */
    private val resourceGraph: MutableMap<ResourceType, MutableMap<String, String>> = mutableMapOf()

    /**
     * Map from resource type to a map of resource name -> XmlContext (for reporting).
     */
    private val resourceLocations: MutableMap<ResourceType, MutableMap<String, XmlContext>> =
        mutableMapOf()

    /**
     * Map from resource type to a map of resource name -> Element (for reporting).
     */
    private val resourceElements: MutableMap<ResourceType, MutableMap<String, Element>> =
        mutableMapOf()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
            folderType == ResourceFolderType.COLOR ||
            folderType == ResourceFolderType.DRAWABLE
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val folderType = context.resourceFolderType ?: return

        when (folderType) {
            ResourceFolderType.VALUES -> visitValuesDocument(context, root)
            ResourceFolderType.COLOR -> visitColorDrawableFile(context, root, ResourceType.COLOR)
            ResourceFolderType.DRAWABLE -> visitColorDrawableFile(context, root, ResourceType.DRAWABLE)
            else -> { /* not applicable */ }
        }
    }

    private fun visitValuesDocument(context: XmlContext, root: Element) {
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                processValueElement(context, node)
            }
        }
    }

    private fun processValueElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> processStyle(context, element)
            TAG_COLOR -> processColorOrDrawableAlias(context, element, ResourceType.COLOR)
            TAG_DRAWABLE -> processColorOrDrawableAlias(context, element, ResourceType.DRAWABLE)
            TAG_ITEM -> processItem(context, element)
        }
    }

    private fun processStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = element.getAttribute(ATTR_PARENT)

        val parentName: String? = when {
            parent.isNotEmpty() -> {
                // parent="@style/Foo" or parent="Foo.Bar" or parent="@android:style/Foo"
                stripStylePrefix(parent)
            }
            name.contains('.') -> {
                // Implicit parent: "Foo.Bar" inherits from "Foo"
                name.substringBeforeLast('.')
            }
            else -> null
        }

        if (parentName != null && parentName.isNotEmpty() && !parentName.startsWith(ANDROID_NS_NAME_PREFIX)) {
            addEdge(ResourceType.STYLE, name, parentName, context, element)
        }
    }

    private fun processColorOrDrawableAlias(context: XmlContext, element: Element, type: ResourceType) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return

        // Check if this is a reference to another resource of the same type
        val ref = extractSameTypeRef(text, type) ?: return
        addEdge(type, name, ref, context, element)
    }

    private fun processItem(context: XmlContext, element: Element) {
        val typeName = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val type = ResourceType.fromXmlValue(typeName) ?: return

        when (type) {
            ResourceType.STYLE -> {
                // <item type="style" name="Foo" parent="Bar"/>
                processStyle(context, element)
            }
            ResourceType.COLOR -> processColorOrDrawableAlias(context, element, ResourceType.COLOR)
            ResourceType.DRAWABLE -> processColorOrDrawableAlias(context, element, ResourceType.DRAWABLE)
            else -> {
                // For other types, check if the text content is a self-reference
                val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
                val text = element.textContent?.trim() ?: return
                val ref = extractSameTypeRef(text, type) ?: return
                addEdge(type, name, ref, context, element)
            }
        }
    }

    private fun visitColorDrawableFile(context: XmlContext, root: Element, type: ResourceType) {
        // In color/ or drawable/ folder, the resource name comes from the filename
        val file = context.file
        val resourceName = file.nameWithoutExtension

        // Check if the root element is a reference (selector, etc. won't be a cycle, but
        // a direct alias like <color> or <drawable> pointing to another resource could be)
        // For file-based resources, we look for direct alias patterns
        val tagName = root.tagName
        if (tagName == TAG_COLOR || tagName == TAG_DRAWABLE) {
            val text = root.textContent?.trim() ?: return
            val ref = extractSameTypeRef(text, type) ?: return
            addEdge(type, resourceName, ref, context, root)
        }
    }

    private fun extractSameTypeRef(text: String, type: ResourceType): String? {
        if (!text.startsWith(PREFIX_RESOURCE_REF)) return null
        if (text.startsWith(ANDROID_NS_NAME_PREFIX, 1)) return null // android: resources

        return when (type) {
            ResourceType.COLOR -> {
                if (text.startsWith(COLOR_RESOURCE_PREFIX)) {
                    text.removePrefix(COLOR_RESOURCE_PREFIX)
                } else null
            }
            ResourceType.DRAWABLE -> {
                if (text.startsWith(DRAWABLE_PREFIX)) {
                    text.removePrefix(DRAWABLE_PREFIX)
                } else null
            }
            ResourceType.STYLE -> {
                if (text.startsWith(STYLE_RESOURCE_PREFIX)) {
                    text.removePrefix(STYLE_RESOURCE_PREFIX)
                } else null
            }
            else -> {
                // Generic: @type/name
                val prefix = "@${type.getName()}/"
                if (text.startsWith(prefix)) {
                    text.removePrefix(prefix)
                } else null
            }
        }
    }

    private fun stripStylePrefix(parent: String): String {
        return when {
            parent.startsWith(STYLE_RESOURCE_PREFIX) -> parent.removePrefix(STYLE_RESOURCE_PREFIX)
            parent.startsWith("@*android:style/") -> parent.removePrefix("@*android:style/").let {
                ANDROID_NS_NAME_PREFIX + it
            }
            parent.startsWith("@android:style/") -> ANDROID_NS_NAME_PREFIX + parent.removePrefix("@android:style/")
            parent.startsWith(PREFIX_RESOURCE_REF) -> {
                // @namespace:type/name or @type/name
                val withoutAt = parent.removePrefix(PREFIX_RESOURCE_REF)
                if (withoutAt.contains('/')) {
                    withoutAt.substringAfter('/')
                } else withoutAt
            }
            else -> parent // bare name or dotted name
        }
    }

    private fun addEdge(
        type: ResourceType,
        from: String,
        to: String,
        context: XmlContext,
        element: Element
    ) {
        val graph = resourceGraph.getOrPut(type) { mutableMapOf() }
        graph[from] = to

        val locations = resourceLocations.getOrPut(type) { mutableMapOf() }
        locations[from] = context

        val elements = resourceElements.getOrPut(type) { mutableMapOf() }
        elements[from] = element
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // Now check for cycles in each resource type graph
        for ((type, graph) in resourceGraph) {
            val reported = mutableSetOf<String>()
            for (start in graph.keys) {
                if (start in reported) continue
                val cycle = findCycle(start, graph) ?: continue
                // Report the cycle
                reportCycle(type, cycle, reported)
            }
        }
    }

    /**
     * Finds a cycle starting from [start] in [graph].
     * Returns the list of nodes forming the cycle, or null if no cycle.
     */
    private fun findCycle(start: String, graph: Map<String, String>): List<String>? {
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        var current: String? = start

        while (current != null && current !in visited) {
            visited.add(current)
            path.add(current)
            current = graph[current]
        }

        if (current == null) return null // no cycle

        // current is in visited - find where the cycle starts
        val cycleStart = path.indexOf(current)
        if (cycleStart == -1) return null // shouldn't happen

        return path.subList(cycleStart, path.size)
    }

    private fun reportCycle(
        type: ResourceType,
        cycle: List<String>,
        reported: MutableSet<String>
    ) {
        val locations = resourceLocations[type] ?: return
        val elements = resourceElements[type] ?: return

        reported.addAll(cycle)

        // Find the best node to report on (one that has a location)
        val reportNode = cycle.firstOrNull { it in locations } ?: return
        val context = locations[reportNode] ?: return
        val element = elements[reportNode] ?: return

        val cycleDescription = buildCycleDescription(cycle, type)
        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "Cycle detected in resource definitions: $cycleDescription"
        )
    }

    private fun buildCycleDescription(cycle: List<String>, type: ResourceType): String {
        val typeName = type.getName()
        val sb = StringBuilder()
        for (i in cycle.indices) {
            val name = cycle[i]
            val next = cycle[(i + 1) % cycle.size]
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append("@$typeName/$name \u2192 @$typeName/$next")
        }
        return sb.toString()
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }
}
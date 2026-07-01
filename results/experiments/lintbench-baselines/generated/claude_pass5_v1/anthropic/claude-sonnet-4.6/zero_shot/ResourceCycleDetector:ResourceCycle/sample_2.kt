/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.COLOR_RESOURCE_PREFIX
import com.android.SdkConstants.DRAWABLE_PREFIX
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
 * Checks for cycles in resource definitions.
 */
class ResourceCycleDetector : ResourceXmlDetector() {

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
                Scope.ALL_RESOURCES_SCOPE
            )
        )

        private const val ATTR_REF = "@"
    }

    /**
     * Map from resource type to a map of resource name -> list of referenced resource names
     * (within the same type).
     */
    private val dependencyMap: MutableMap<ResourceType, MutableMap<String, MutableList<String>>> =
        mutableMapOf()

    /**
     * Map from resource type to a map of resource name -> the XML element for error reporting.
     */
    private val elementMap: MutableMap<ResourceType, MutableMap<String, Element>> = mutableMapOf()

    /**
     * Map from resource type to a map of resource name -> context for error reporting.
     */
    private val contextMap: MutableMap<ResourceType, MutableMap<String, XmlContext>> = mutableMapOf()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
            folderType == ResourceFolderType.COLOR ||
            folderType == ResourceFolderType.DRAWABLE
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val folderType = context.resourceFolderType ?: return

        when (folderType) {
            ResourceFolderType.VALUES -> visitValueDocument(context, root)
            ResourceFolderType.COLOR -> visitColorStateListDocument(context, root)
            ResourceFolderType.DRAWABLE -> visitDrawableDocument(context, root)
            else -> { /* not handled */ }
        }
    }

    private fun visitValueDocument(context: XmlContext, root: Element) {
        var child = root.firstChild
        while (child != null) {
            if (child is Element) {
                when (child.tagName) {
                    TAG_STYLE -> visitStyleElement(context, child)
                    TAG_COLOR -> visitColorElement(context, child)
                    TAG_DRAWABLE -> visitDrawableElement(context, child)
                    TAG_ITEM -> visitItemElement(context, child)
                }
            }
            child = child.nextSibling
        }
    }

    private fun visitStyleElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = element.getAttribute(ATTR_PARENT)

        if (parent.isNotEmpty() && !parent.startsWith(ANDROID_NS_NAME_PREFIX)) {
            // Strip the style resource prefix if present
            val parentName = when {
                parent.startsWith(STYLE_RESOURCE_PREFIX) -> parent.substring(STYLE_RESOURCE_PREFIX.length)
                parent.startsWith("@android:style/") -> return // Android framework style, skip
                else -> parent
            }
            addDependency(ResourceType.STYLE, name, parentName, element, context)
        } else if (parent.isEmpty()) {
            // Check implicit parent via dot notation (e.g., "MyTheme.Child" -> "MyTheme")
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = name.substring(0, dotIndex)
                addDependency(ResourceType.STYLE, name, implicitParent, element, context)
            }
        }
    }

    private fun visitColorElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(COLOR_RESOURCE_PREFIX)) {
            val refName = text.substring(COLOR_RESOURCE_PREFIX.length)
            if (!refName.startsWith("android:")) {
                addDependency(ResourceType.COLOR, name, refName, element, context)
            }
        }
    }

    private fun visitDrawableElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(DRAWABLE_PREFIX)) {
            val refName = text.substring(DRAWABLE_PREFIX.length)
            if (!refName.startsWith("android:")) {
                addDependency(ResourceType.DRAWABLE, name, refName, element, context)
            }
        }
    }

    private fun visitItemElement(context: XmlContext, element: Element) {
        val type = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val resourceType = ResourceType.fromXmlValue(type) ?: return

        val text = element.textContent?.trim() ?: return
        if (text.startsWith(ATTR_REF)) {
            val ref = text.substring(1) // remove '@'
            val prefix = when (resourceType) {
                ResourceType.COLOR -> "color/"
                ResourceType.DRAWABLE -> "drawable/"
                ResourceType.STYLE -> "style/"
                ResourceType.LAYOUT -> "layout/"
                else -> "$type/"
            }
            val refValue = when {
                ref.startsWith(prefix) -> ref.substring(prefix.length)
                ref.startsWith("android:") -> return // Android framework resource, skip
                ref.contains("/") -> {
                    // Different type reference
                    val slashIdx = ref.indexOf('/')
                    val refType = ref.substring(0, slashIdx)
                    val refResType = ResourceType.fromXmlValue(refType)
                    if (refResType != resourceType) return // Cross-type reference
                    ref.substring(slashIdx + 1)
                }
                else -> ref
            }
            if (refValue.isNotEmpty() && !refValue.startsWith("android:")) {
                addDependency(resourceType, name, refValue, element, context)
            }
        }
    }

    private fun visitColorStateListDocument(context: XmlContext, root: Element) {
        // Color state list files can reference other colors
        // Check the android:color attribute in item elements
        val fileName = context.file.nameWithoutExtension
        var child = root.firstChild
        while (child != null) {
            if (child is Element && child.tagName == TAG_ITEM) {
                val colorAttr = child.getAttribute(ATTR_COLOR)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttributeNS("http://schemas.android.com/apk/res/android", ATTR_COLOR)
                if (colorAttr.startsWith(COLOR_RESOURCE_PREFIX)) {
                    val refName = colorAttr.substring(COLOR_RESOURCE_PREFIX.length)
                    if (!refName.startsWith("android:")) {
                        addDependency(ResourceType.COLOR, fileName, refName, child, context)
                    }
                }
            }
            child = child.nextSibling
        }
    }

    private fun visitDrawableDocument(context: XmlContext, root: Element) {
        val fileName = context.file.nameWithoutExtension
        // Check for drawable references in drawable XML files
        checkElementForDrawableRef(context, root, fileName)
        var child = root.firstChild
        while (child != null) {
            if (child is Element) {
                checkElementForDrawableRef(context, child, fileName)
            }
            child = child.nextSibling
        }
    }

    private fun checkElementForDrawableRef(context: XmlContext, element: Element, fileName: String) {
        val drawableAttr = element.getAttribute(ATTR_DRAWABLE)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttributeNS("http://schemas.android.com/apk/res/android", ATTR_DRAWABLE)
        if (drawableAttr.startsWith(DRAWABLE_PREFIX)) {
            val refName = drawableAttr.substring(DRAWABLE_PREFIX.length)
            if (!refName.startsWith("android:")) {
                addDependency(ResourceType.DRAWABLE, fileName, refName, element, context)
            }
        }
    }

    private fun addDependency(
        type: ResourceType,
        from: String,
        to: String,
        element: Element,
        context: XmlContext
    ) {
        val typeDeps = dependencyMap.getOrPut(type) { mutableMapOf() }
        val deps = typeDeps.getOrPut(from) { mutableListOf() }
        deps.add(to)

        val typeElements = elementMap.getOrPut(type) { mutableMapOf() }
        if (!typeElements.containsKey(from)) {
            typeElements[from] = element
        }

        val typeContexts = contextMap.getOrPut(type) { mutableMapOf() }
        if (!typeContexts.containsKey(from)) {
            typeContexts[from] = context
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // After processing all files, detect cycles
        for ((type, deps) in dependencyMap) {
            val reported = mutableSetOf<String>()
            for (name in deps.keys) {
                if (!reported.contains(name)) {
                    val cycle = findCycle(name, type, deps)
                    if (cycle != null) {
                        // Report the cycle
                        val cycleStart = cycle.first
                        val cyclePath = cycle.second

                        val typeElements = elementMap[type] ?: continue
                        val typeContexts = contextMap[type] ?: continue

                        val element = typeElements[cycleStart] ?: continue
                        val ctx = typeContexts[cycleStart] ?: continue

                        val cycleDescription = buildCycleDescription(cycleStart, cyclePath, type)
                        ctx.report(
                            ISSUE,
                            element,
                            ctx.getLocation(element),
                            cycleDescription
                        )

                        // Mark all nodes in the cycle as reported to avoid duplicate reports
                        reported.add(cycleStart)
                        reported.addAll(cyclePath)
                    }
                }
            }
        }
    }

    /**
     * Finds a cycle starting from [start] in the dependency graph [deps].
     * Returns a pair of (cycle start node, list of nodes in the cycle path) if a cycle is found,
     * or null if no cycle exists.
     */
    private fun findCycle(
        start: String,
        type: ResourceType,
        deps: Map<String, List<String>>
    ): Pair<String, List<String>>? {
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val pathSet = mutableSetOf<String>()

        fun dfs(node: String): Pair<String, List<String>>? {
            if (pathSet.contains(node)) {
                // Found a cycle - find where the cycle starts
                val cycleStartIndex = path.indexOf(node)
                return Pair(node, path.subList(cycleStartIndex, path.size).toList())
            }
            if (visited.contains(node)) return null

            visited.add(node)
            path.add(node)
            pathSet.add(node)

            val neighbors = deps[node] ?: emptyList()
            for (neighbor in neighbors) {
                val result = dfs(neighbor)
                if (result != null) return result
            }

            path.removeAt(path.size - 1)
            pathSet.remove(node)
            return null
        }

        return dfs(start)
    }

    private fun buildCycleDescription(
        cycleStart: String,
        cyclePath: List<String>,
        type: ResourceType
    ): String {
        val typeStr = type.getName()
        val sb = StringBuilder()
        sb.append("Cycle detected: ")
        sb.append(typeStr).append("/").append(cycleStart)
        for (node in cyclePath) {
            if (node != cycleStart) {
                sb.append(" => ").append(typeStr).append("/").append(node)
            }
        }
        sb.append(" => ").append(typeStr).append("/").append(cycleStart)
        return sb.toString()
    }
}
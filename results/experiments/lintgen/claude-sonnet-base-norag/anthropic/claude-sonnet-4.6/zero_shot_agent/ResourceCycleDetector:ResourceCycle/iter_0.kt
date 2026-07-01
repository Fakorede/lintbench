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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
    private val styleParents = mutableMapOf<String, String>()       // style name -> parent style name
    private val colorRefs = mutableMapOf<String, String>()          // color name -> referenced color name
    private val drawableRefs = mutableMapOf<String, String>()       // drawable name -> referenced drawable name

    // Location map for reporting: "type:name" -> XmlContext + Element
    private val locations = mutableMapOf<String, Pair<XmlContext, Element>>()

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
    }

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
            ResourceFolderType.COLOR -> visitColorDocument(context, root)
            ResourceFolderType.DRAWABLE -> visitDrawableDocument(context, root)
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
                TAG_COLOR -> handleColorValueElement(context, node)
                TAG_DRAWABLE -> handleDrawableValueElement(context, node)
                TAG_ITEM -> {
                    val type = node.getAttribute(ATTR_TYPE)
                    when (type) {
                        KEY_STYLE -> handleStyleElement(context, node)
                        KEY_COLOR -> handleColorValueElement(context, node)
                        KEY_DRAWABLE -> handleDrawableValueElement(context, node)
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
            // Strip @style/ prefix if present
            if (parent.startsWith(STYLE_RESOURCE_PREFIX)) {
                parent = parent.substring(STYLE_RESOURCE_PREFIX.length)
            } else if (parent.startsWith("@android:style/")) {
                // References to android framework styles can't cycle with app styles
                return
            }
            val normalizedParent = parent.replace('.', '_')
            styleParents[normalizedName] = normalizedParent
            locations["$KEY_STYLE:$normalizedName"] = Pair(context, element)
        } else {
            // Check for implicit parent via dot notation: "ParentStyle.ChildStyle"
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = name.substring(0, dotIndex).replace('.', '_')
                styleParents[normalizedName] = implicitParent
                locations["$KEY_STYLE:$normalizedName"] = Pair(context, element)
            }
        }
    }

    private fun handleColorValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(COLOR_RESOURCE_PREFIX)) {
            val refName = text.substring(COLOR_RESOURCE_PREFIX.length).trim()
            if (!refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                colorRefs[name] = refName
                locations["$KEY_COLOR:$name"] = Pair(context, element)
            }
        }
    }

    private fun handleDrawableValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(DRAWABLE_PREFIX)) {
            val refName = text.substring(DRAWABLE_PREFIX.length).trim()
            if (!refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                drawableRefs[name] = refName
                locations["$KEY_DRAWABLE:$name"] = Pair(context, element)
            }
        }
    }

    private fun visitColorDocument(context: XmlContext, root: Element) {
        // A color selector file can reference other colors via @color/
        // We look at the root element's attributes for color references
        checkElementForColorRef(context, root)
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                checkElementForColorRef(context, node)
            }
        }
    }

    private fun checkElementForColorRef(context: XmlContext, element: Element) {
        val fileName = context.file.nameWithoutExtension
        val attrs = element.attributes ?: return
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val value = attr.nodeValue ?: continue
            if (value.startsWith(COLOR_RESOURCE_PREFIX)) {
                val refName = value.substring(COLOR_RESOURCE_PREFIX.length)
                if (!refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                    colorRefs[fileName] = refName
                    locations["$KEY_COLOR:$fileName"] = Pair(context, element)
                }
            }
        }
    }

    private fun visitDrawableDocument(context: XmlContext, root: Element) {
        val fileName = context.file.nameWithoutExtension
        checkElementForDrawableRef(context, root, fileName)
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                checkElementForDrawableRef(context, node, fileName)
            }
        }
    }

    private fun checkElementForDrawableRef(context: XmlContext, element: Element, fileName: String) {
        val attrs = element.attributes ?: return
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val value = attr.nodeValue ?: continue
            if (value.startsWith(DRAWABLE_PREFIX)) {
                val refName = value.substring(DRAWABLE_PREFIX.length)
                if (!refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                    drawableRefs[fileName] = refName
                    locations["$KEY_DRAWABLE:$fileName"] = Pair(context, element)
                }
            }
        }
        // Also check text content for <item> elements
        val text = element.textContent?.trim() ?: return
        if (text.startsWith(DRAWABLE_PREFIX)) {
            val refName = text.substring(DRAWABLE_PREFIX.length)
            if (!refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                drawableRefs[fileName] = refName
                locations["$KEY_DRAWABLE:$fileName"] = Pair(context, element)
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Check for cycles in styles
        detectCycles(KEY_STYLE, styleParents, context)
        // Check for cycles in colors
        detectCycles(KEY_COLOR, colorRefs, context)
        // Check for cycles in drawables
        detectCycles(KEY_DRAWABLE, drawableRefs, context)
    }

    private fun detectCycles(
        resourceType: String,
        graph: Map<String, String>,
        context: Context
    ) {
        // Track which nodes have been fully processed (no cycle found from them)
        val visited = mutableSetOf<String>()
        // Track nodes reported to avoid duplicate reports
        val reported = mutableSetOf<String>()

        for (startNode in graph.keys) {
            if (startNode in visited) continue

            // Walk the chain from startNode
            val chain = mutableListOf<String>()
            val chainSet = mutableSetOf<String>()
            var current: String? = startNode

            while (current != null && current !in visited) {
                if (current in chainSet) {
                    // Found a cycle - current is the start of the cycle
                    val cycleStart = current
                    val cycleIndex = chain.indexOf(cycleStart)
                    val cycle = chain.subList(cycleIndex, chain.size)

                    // Report the cycle for each node in the cycle that has a location
                    for (nodeName in cycle) {
                        if (nodeName !in reported) {
                            reported.add(nodeName)
                            val locationKey = "$resourceType:$nodeName"
                            val locationInfo = locations[locationKey]
                            if (locationInfo != null) {
                                val (xmlContext, element) = locationInfo
                                val cycleDescription = buildCycleDescription(resourceType, cycle, nodeName)
                                xmlContext.report(
                                    ISSUE,
                                    element,
                                    xmlContext.getLocation(element),
                                    cycleDescription
                                )
                            }
                        }
                    }
                    break
                }

                chain.add(current)
                chainSet.add(current)
                current = graph[current]
            }

            // Mark all nodes in this chain as visited
            visited.addAll(chain)
        }
    }

    private fun buildCycleDescription(
        resourceType: String,
        cycle: List<String>,
        startNode: String
    ): String {
        val prefix = when (resourceType) {
            KEY_STYLE -> "@style/"
            KEY_COLOR -> "@color/"
            KEY_DRAWABLE -> "@drawable/"
            else -> "@$resourceType/"
        }

        val sb = StringBuilder()
        sb.append("Cycle detected: ")

        // Build the cycle starting from startNode
        val startIndex = cycle.indexOf(startNode)
        val orderedCycle = cycle.subList(startIndex, cycle.size) + cycle.subList(0, startIndex)

        for (i in orderedCycle.indices) {
            sb.append("$prefix${orderedCycle[i]}")
            if (i < orderedCycle.size - 1) {
                sb.append(" => ")
            }
        }
        sb.append(" => $prefix${orderedCycle[0]}")

        return sb.toString()
    }
}
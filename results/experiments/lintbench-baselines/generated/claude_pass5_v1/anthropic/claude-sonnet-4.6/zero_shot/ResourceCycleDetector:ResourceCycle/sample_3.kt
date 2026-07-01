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
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Checks for cycles in resource definitions (e.g. a style that extends itself,
 * or a color that references itself).
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    // Maps resource type -> resource name -> list of names it references
    private val resourceReferences = mutableMapOf<ResourceType, MutableMap<String, MutableList<String>>>()

    // Maps resource type -> resource name -> XmlContext + Element for reporting
    private val resourceLocations = mutableMapOf<ResourceType, MutableMap<String, Pair<XmlContext, Element>>>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_ITEM,
            "drawable",
            "integer",
            "bool",
            "dimen",
            "string",
            "array",
            "plurals",
            "fraction",
            "id",
            "attr"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> visitStyle(context, element)
            else -> visitGenericResource(context, element)
        }
    }

    private fun visitStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = element.getAttribute(ATTR_PARENT)

        val resolvedParent: String? = when {
            parent.isNotEmpty() -> {
                // parent attribute explicitly set
                stripResourcePrefix(parent, ResourceType.STYLE)
            }
            name.contains('.') -> {
                // Implicit parent via dot notation: "Parent.Child" -> "Parent"
                name.substringBeforeLast('.')
            }
            else -> null
        }

        if (resolvedParent != null && !resolvedParent.startsWith(ANDROID_NS_NAME_PREFIX)) {
            addReference(ResourceType.STYLE, name, resolvedParent)
            recordLocation(ResourceType.STYLE, name, context, element)
        } else {
            recordLocation(ResourceType.STYLE, name, context, element)
        }
    }

    private fun visitGenericResource(context: XmlContext, element: Element) {
        val tag = element.tagName
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        // Determine resource type from tag
        val resourceType = when (tag) {
            TAG_COLOR, "color" -> ResourceType.COLOR
            "drawable" -> ResourceType.DRAWABLE
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "dimen" -> ResourceType.DIMEN
            "string" -> ResourceType.STRING
            "array", "string-array", "integer-array" -> ResourceType.ARRAY
            "plurals" -> ResourceType.PLURALS
            "fraction" -> ResourceType.FRACTION
            "id" -> ResourceType.ID
            "attr" -> ResourceType.ATTR
            TAG_ITEM -> {
                val typeAttr = element.getAttribute(ATTR_TYPE)
                ResourceType.fromXmlTagName(typeAttr) ?: return
            }
            else -> return
        }

        recordLocation(resourceType, name, context, element)

        // Check text content for a resource reference
        val textContent = getTextContent(element).trim()
        if (textContent.startsWith(PREFIX_RESOURCE_REF)) {
            val refName = stripResourcePrefix(textContent, resourceType)
            if (refName != null && !refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                addReference(resourceType, name, refName)
            }
        }

        // Also check drawable and color attributes
        checkAttributeReference(element, ATTR_DRAWABLE, ResourceType.DRAWABLE, name, resourceType)
        checkAttributeReference(element, ATTR_COLOR, ResourceType.COLOR, name, resourceType)
    }

    private fun checkAttributeReference(
        element: Element,
        attrName: String,
        refType: ResourceType,
        resourceName: String,
        resourceType: ResourceType
    ) {
        if (resourceType != refType) return
        val attrValue = element.getAttribute(attrName).takeIf { it.isNotEmpty() } ?: return
        if (attrValue.startsWith(PREFIX_RESOURCE_REF)) {
            val refName = stripResourcePrefix(attrValue, refType)
            if (refName != null && !refName.startsWith(ANDROID_NS_NAME_PREFIX)) {
                addReference(resourceType, resourceName, refName)
            }
        }
    }

    private fun addReference(type: ResourceType, from: String, to: String) {
        resourceReferences
            .getOrPut(type) { mutableMapOf() }
            .getOrPut(from) { mutableListOf() }
            .add(to)
    }

    private fun recordLocation(type: ResourceType, name: String, context: XmlContext, element: Element) {
        resourceLocations
            .getOrPut(type) { mutableMapOf() }
            .putIfAbsent(name, Pair(context, element))
    }

    override fun afterCheckRootProject(context: Context) {
        for ((type, refs) in resourceReferences) {
            val reported = mutableSetOf<String>()
            for (name in refs.keys) {
                if (name !in reported) {
                    val cycle = findCycle(name, type, refs)
                    if (cycle != null) {
                        // Report on each element in the cycle
                        for (node in cycle) {
                            if (node !in reported) {
                                reported.add(node)
                                val locationInfo = resourceLocations[type]?.get(node)
                                if (locationInfo != null) {
                                    val (xmlContext, element) = locationInfo
                                    val cycleStr = formatCycle(cycle, node)
                                    xmlContext.report(
                                        ISSUE,
                                        element,
                                        xmlContext.getLocation(element),
                                        "Resource `@${type.getName()}/$node` has a cycle: $cycleStr"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds a cycle reachable from [start] in the given reference graph.
     * Returns the list of nodes forming the cycle, or null if no cycle exists.
     */
    private fun findCycle(
        start: String,
        type: ResourceType,
        refs: Map<String, List<String>>
    ): List<String>? {
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val pathSet = mutableSetOf<String>()

        fun dfs(node: String): List<String>? {
            if (node in pathSet) {
                // Found a cycle - extract the cycle portion
                val cycleStart = path.indexOf(node)
                return path.subList(cycleStart, path.size).toList()
            }
            if (node in visited) return null

            visited.add(node)
            path.add(node)
            pathSet.add(node)

            val neighbors = refs[node] ?: emptyList()
            for (neighbor in neighbors) {
                val cycle = dfs(neighbor)
                if (cycle != null) return cycle
            }

            path.removeAt(path.size - 1)
            pathSet.remove(node)
            return null
        }

        return dfs(start)
    }

    private fun formatCycle(cycle: List<String>, startNode: String): String {
        val idx = cycle.indexOf(startNode)
        val rotated = if (idx >= 0) {
            cycle.subList(idx, cycle.size) + cycle.subList(0, idx)
        } else {
            cycle
        }
        return rotated.joinToString(" -> ") + " -> " + rotated.first()
    }

    /**
     * Strips the resource reference prefix (e.g. "@style/", "@color/", "@+id/") from a value,
     * returning just the resource name. Returns null if the reference is to a different type
     * or cannot be parsed.
     */
    private fun stripResourcePrefix(value: String, expectedType: ResourceType): String? {
        var v = value.trim()
        if (v.startsWith("@")) {
            v = v.substring(1)
        } else {
            return null
        }
        // Remove leading '+'
        if (v.startsWith("+")) {
            v = v.substring(1)
        }
        // Remove namespace prefix like "android:"
        val colonIdx = v.indexOf(':')
        val slashIdx = v.indexOf('/')
        if (colonIdx >= 0 && (slashIdx < 0 || colonIdx < slashIdx)) {
            val ns = v.substring(0, colonIdx)
            if (ns == "android") {
                return ANDROID_NS_NAME_PREFIX + v.substring(slashIdx + 1)
            }
            // Other namespaces - skip
            v = v.substring(colonIdx + 1)
        }
        if (slashIdx < 0 && colonIdx < 0) {
            // No type prefix, assume same type
            return v
        }
        val actualSlash = v.indexOf('/')
        if (actualSlash < 0) return v

        val typeStr = v.substring(0, actualSlash)
        val resourceName = v.substring(actualSlash + 1)

        val refType = ResourceType.fromXmlValue(typeStr) ?: ResourceType.fromXmlTagName(typeStr)
        if (refType != null && refType != expectedType) {
            // Reference to a different type - not a same-type cycle candidate
            return null
        }

        return resourceName
    }

    private fun getTextContent(element: Element): String {
        val sb = StringBuilder()
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                sb.append(child.nodeValue)
            }
            child = child.nextSibling
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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
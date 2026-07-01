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

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
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
import org.w3c.dom.Node

/**
 * Checks for cycles in resource definitions.
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    /**
     * Map from resource type to a map of resource name to the resource name it references.
     * For styles: style name -> parent style name
     * For colors/drawables etc.: resource name -> referenced resource name
     */
    private val dependencyMap: MutableMap<ResourceType, MutableMap<String, String>> = mutableMapOf()

    /**
     * Map from resource type to a map of resource name to the location (file + element) for
     * reporting purposes.
     */
    private val locationMap: MutableMap<ResourceType, MutableMap<String, XmlContext>> = mutableMapOf()
    private val elementMap: MutableMap<ResourceType, MutableMap<String, Element>> = mutableMapOf()

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

        private val RESOURCE_TYPES_WITH_POTENTIAL_CYCLES = setOf(
            ResourceType.STYLE,
            ResourceType.COLOR,
            ResourceType.DRAWABLE,
            ResourceType.LAYOUT,
            ResourceType.DIMEN,
            ResourceType.STRING,
            ResourceType.INTEGER,
            ResourceType.BOOL,
            ResourceType.ARRAY,
            ResourceType.ATTR,
            ResourceType.PLURALS
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
                folderType == ResourceFolderType.COLOR ||
                folderType == ResourceFolderType.DRAWABLE ||
                folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // handled via visitElement
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return

        when (folderType) {
            ResourceFolderType.VALUES -> visitValueElement(context, element)
            ResourceFolderType.COLOR,
            ResourceFolderType.DRAWABLE -> visitFileReferenceElement(context, element, folderType)
            else -> { /* not handled */ }
        }
    }

    private fun visitValueElement(context: XmlContext, element: Element) {
        val tagName = element.tagName

        // Handle <style> elements
        if (tagName == TAG_STYLE) {
            val name = element.getAttribute(ATTR_NAME)
            if (name.isNullOrEmpty()) return

            // Parent can be specified via the parent attribute or via dot notation
            val parentAttr = element.getAttribute(ATTR_PARENT)
            val parent: String? = when {
                !parentAttr.isNullOrEmpty() -> {
                    // Strip namespace prefix if present (e.g. "@style/Foo" -> "Foo", or "android:Theme" -> skip android styles)
                    val stripped = stripStyleReference(parentAttr)
                    stripped
                }
                name.contains('.') -> {
                    // Implicit parent via dot notation: "MyTheme.Child" -> parent is "MyTheme"
                    name.substringBeforeLast('.')
                }
                else -> null
            }

            if (parent != null && parent.isNotEmpty()) {
                recordDependency(context, element, ResourceType.STYLE, name, parent)
            }
            return
        }

        // Handle <item> elements and other value elements
        val resourceType = getResourceTypeForTag(tagName, element) ?: return
        val name = element.getAttribute(ATTR_NAME)
        if (name.isNullOrEmpty()) return

        // Check if the element's text content is a reference to another resource of the same type
        val reference = getTextReference(element) ?: return
        val refName = extractResourceReference(reference, resourceType) ?: return

        recordDependency(context, element, resourceType, name, refName)
    }

    private fun visitFileReferenceElement(
        context: XmlContext,
        element: Element,
        folderType: ResourceFolderType
    ) {
        // For color and drawable files, look for references within the file
        // e.g. a color state list that references another color
        val resourceType = when (folderType) {
            ResourceFolderType.COLOR -> ResourceType.COLOR
            ResourceFolderType.DRAWABLE -> ResourceType.DRAWABLE
            else -> return
        }

        // Get the resource name from the file name
        val file = context.file
        val resourceName = file.nameWithoutExtension

        // Look for color/drawable references in attributes
        val nodeMap = element.attributes
        for (i in 0 until nodeMap.length) {
            val attr = nodeMap.item(i)
            val value = attr.nodeValue ?: continue
            val refName = extractResourceReference(value, resourceType) ?: continue
            if (refName != resourceName) {
                recordDependency(context, element, resourceType, resourceName, refName)
            }
        }
    }

    private fun getResourceTypeForTag(tagName: String, element: Element): ResourceType? {
        return when (tagName) {
            "color" -> ResourceType.COLOR
            "dimen" -> ResourceType.DIMEN
            "string" -> ResourceType.STRING
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "drawable" -> ResourceType.DRAWABLE
            "style" -> ResourceType.STYLE
            "attr" -> ResourceType.ATTR
            "plurals" -> ResourceType.PLURALS
            TAG_ITEM -> {
                // <item type="color" name="...">
                val typeAttr = element.getAttribute(ATTR_TYPE)
                if (!typeAttr.isNullOrEmpty()) {
                    ResourceType.fromXmlValue(typeAttr)
                } else null
            }
            else -> null
        }
    }

    private fun getTextReference(element: Element): String? {
        val child = element.firstChild
        if (child != null && child.nodeType == Node.TEXT_NODE) {
            val text = child.nodeValue?.trim() ?: return null
            if (text.startsWith("@") || text.startsWith("?")) {
                return text
            }
        }
        return null
    }

    private fun extractResourceReference(value: String, expectedType: ResourceType): String? {
        // Matches patterns like @color/foo, @dimen/bar, @style/MyStyle, ?attr/foo
        val trimmed = value.trim()
        if (!trimmed.startsWith("@") && !trimmed.startsWith("?")) return null

        // Remove the leading @ or ?
        val withoutPrefix = trimmed.substring(1)

        // Remove optional namespace (e.g. "android:" prefix -> skip android resources)
        val withoutNamespace = if (withoutPrefix.contains(':')) {
            val namespace = withoutPrefix.substringBefore(':')
            if (namespace == "android") return null // skip android framework references
            withoutPrefix.substringAfter(':')
        } else {
            withoutPrefix
        }

        // Remove optional + (for creating new IDs)
        val withoutPlus = if (withoutNamespace.startsWith("+")) {
            withoutNamespace.substring(1)
        } else {
            withoutNamespace
        }

        // Should be in format "type/name"
        if (!withoutPlus.contains('/')) return null

        val type = withoutPlus.substringBefore('/')
        val name = withoutPlus.substringAfter('/')

        val refType = ResourceType.fromXmlValue(type) ?: return null
        if (refType != expectedType) return null

        return name
    }

    private fun stripStyleReference(reference: String): String? {
        val trimmed = reference.trim()

        // If it starts with @style/ or @android:style/, extract the style name
        if (trimmed.startsWith("@")) {
            val withoutAt = trimmed.substring(1)
            // Skip android: namespace
            if (withoutAt.startsWith("android:")) return null
            val withoutNamespace = if (withoutAt.contains(':')) {
                withoutAt.substringAfter(':')
            } else {
                withoutAt
            }
            if (withoutNamespace.startsWith("style/")) {
                return withoutNamespace.substringAfter("style/")
            }
            return null
        }

        // If it's just a plain name (possibly with dots), it's a direct style reference
        // but skip if it contains android: namespace
        if (trimmed.contains(':')) {
            val namespace = trimmed.substringBefore(':')
            if (namespace == "android") return null
            return trimmed.substringAfter(':')
        }

        return if (trimmed.isNotEmpty()) trimmed else null
    }

    private fun recordDependency(
        context: XmlContext,
        element: Element,
        type: ResourceType,
        name: String,
        referencedName: String
    ) {
        val typeDeps = dependencyMap.getOrPut(type) { mutableMapOf() }
        typeDeps[name] = referencedName

        val typeLocations = locationMap.getOrPut(type) { mutableMapOf() }
        typeLocations[name] = context

        val typeElements = elementMap.getOrPut(type) { mutableMapOf() }
        typeElements[name] = element
    }

    override fun afterCheckRootProject(context: Context) {
        // Now check for cycles in all collected dependencies
        for ((type, deps) in dependencyMap) {
            val visited = mutableSetOf<String>()
            val reported = mutableSetOf<String>()

            for (name in deps.keys) {
                if (name !in visited) {
                    detectCycle(context, type, name, deps, visited, reported)
                }
            }
        }
    }

    private fun detectCycle(
        context: Context,
        type: ResourceType,
        startName: String,
        deps: Map<String, String>,
        visited: MutableSet<String>,
        reported: MutableSet<String>
    ) {
        // Use iterative DFS to detect cycles
        val path = mutableListOf<String>()
        val pathSet = mutableSetOf<String>()
        val stack = ArrayDeque<Pair<String, Boolean>>() // name, isBacktrack

        stack.addLast(Pair(startName, false))

        while (stack.isNotEmpty()) {
            val (current, isBacktrack) = stack.removeLast()

            if (isBacktrack) {
                path.removeLastOrNull()
                pathSet.remove(current)
                continue
            }

            if (current in pathSet) {
                // Found a cycle - current is already in our path
                val cycleStart = path.indexOf(current)
                if (cycleStart >= 0) {
                    val cycle = path.subList(cycleStart, path.size).toMutableList()
                    cycle.add(current)

                    // Report the cycle for the first element we have location info for
                    for (cycleMember in cycle.dropLast(1)) {
                        if (cycleMember !in reported) {
                            reported.add(cycleMember)
                            reportCycle(context, type, cycleMember, cycle)
                            break
                        }
                    }
                }
                continue
            }

            if (current in visited) {
                continue
            }

            visited.add(current)
            path.add(current)
            pathSet.add(current)

            // Add backtrack marker
            stack.addLast(Pair(current, true))

            val next = deps[current]
            if (next != null) {
                stack.addLast(Pair(next, false))
            }
        }
    }

    private fun reportCycle(
        context: Context,
        type: ResourceType,
        name: String,
        cycle: List<String>
    ) {
        val xmlContext = locationMap[type]?.get(name) ?: return
        val element = elementMap[type]?.get(name) ?: return

        val cycleDescription = cycle.joinToString(" => ")
        val message = "Cycle detected: $cycleDescription"

        val location = xmlContext.getElementLocation(element)
        context.report(ISSUE, location, message)
    }
}
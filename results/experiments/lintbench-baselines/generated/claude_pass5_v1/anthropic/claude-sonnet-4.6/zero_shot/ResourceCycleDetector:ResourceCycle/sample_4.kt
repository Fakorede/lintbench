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
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks for cycles in resource definitions (e.g. style A extends style B which extends style A).
 */
class ResourceCycleDetector : ResourceXmlDetector() {

    /**
     * Map from resource type to a map of resource name -> list of referenced resource names
     * (within the same type).
     */
    private val graph: MutableMap<ResourceType, MutableMap<String, MutableList<String>>> =
        mutableMapOf()

    /**
     * Map from resource type to a map of resource name -> XmlContext + Element (for reporting).
     */
    private val locations: MutableMap<ResourceType, MutableMap<String, Pair<XmlContext, Element>>> =
        mutableMapOf()

    // -----------------------------------------------------------------------
    // ResourceXmlDetector overrides
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_STYLE, TAG_ITEM, TAG_COLOR, "drawable", "string", "bool", "integer",
            "dimen", "fraction", "array", "integer-array", "string-array", "plurals",
            "attr", "declare-styleable")

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName

        when (tag) {
            TAG_STYLE -> handleStyle(context, element)
            TAG_ITEM  -> handleItem(context, element)
            else      -> handleValueElement(context, element, tag)
        }
    }

    // -----------------------------------------------------------------------
    // Per-element handlers
    // -----------------------------------------------------------------------

    private fun handleStyle(context: XmlContext, element: Element) {
        val name   = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = element.getAttribute(ATTR_PARENT)

        // Normalise dots/slashes to dots for style name comparison
        val normName = normStyleName(name)

        // Record location for later reporting
        addLocation(ResourceType.STYLE, normName, context, element)

        // Determine parent: explicit "parent" attribute or implicit dot-notation parent
        val parentName: String? = when {
            parent.isNotEmpty() -> normStyleName(stripResourcePrefix(parent))
            name.contains('.')  -> {
                // Implicit parent: everything before the last dot
                val lastDot = name.lastIndexOf('.')
                normStyleName(name.substring(0, lastDot))
            }
            else -> null
        }

        if (!parentName.isNullOrEmpty()) {
            addEdge(ResourceType.STYLE, normName, parentName)
        }
    }

    private fun handleItem(context: XmlContext, element: Element) {
        // <item type="color" name="foo">@color/bar</item>  etc.
        val typeName = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val resType  = ResourceType.fromXmlValue(typeName) ?: return
        val name     = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        addLocation(resType, name, context, element)

        val refName = extractReference(element.textContent?.trim(), resType) ?: return
        addEdge(resType, name, refName)
    }

    private fun handleValueElement(context: XmlContext, element: Element, tag: String) {
        val resType = tagToResourceType(tag) ?: return
        val name    = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        addLocation(resType, name, context, element)

        val refName = extractReference(element.textContent?.trim(), resType) ?: return
        addEdge(resType, name, refName)
    }

    // -----------------------------------------------------------------------
    // After-project callback – detect cycles
    // -----------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        for ((resType, adjacency) in graph) {
            val visited   = mutableSetOf<String>()
            val inStack   = mutableSetOf<String>()

            for (node in adjacency.keys) {
                if (node !in visited) {
                    detectCycle(context, resType, node, adjacency, visited, inStack, mutableListOf())
                }
            }
        }
    }

    // DFS cycle detection – reports the first back-edge found per cycle
    private fun detectCycle(
        context: Context,
        resType: ResourceType,
        node: String,
        adjacency: Map<String, List<String>>,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>
    ) {
        visited.add(node)
        inStack.add(node)
        path.add(node)

        for (neighbour in adjacency[node].orEmpty()) {
            if (neighbour !in visited) {
                if (neighbour in adjacency) {
                    detectCycle(context, resType, neighbour, adjacency, visited, inStack, path)
                }
                // else neighbour is a leaf / defined elsewhere – no cycle through it
            } else if (neighbour in inStack) {
                // Found a cycle – report it
                reportCycle(context, resType, path, neighbour)
            }
        }

        path.removeAt(path.size - 1)
        inStack.remove(node)
    }

    private fun reportCycle(
        context: Context,
        resType: ResourceType,
        path: List<String>,
        cycleStart: String
    ) {
        // Build the cycle description
        val cycleIndex = path.indexOf(cycleStart)
        val cyclePath  = if (cycleIndex >= 0) path.subList(cycleIndex, path.size) else path
        val prefix     = "@${resType.getName()}/"
        val cycleStr   = (cyclePath + cycleStart).joinToString(" => ") { "$prefix$it" }

        // Find the best location to report (the node that closes the cycle)
        val reportNode = cyclePath.last()
        val locMap     = locations[resType]
        val locPair    = locMap?.get(reportNode)

        val message = "Cycle detected in resource definitions: $cycleStr"

        if (locPair != null) {
            val (xmlContext, element) = locPair
            xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message)
        } else {
            // Fallback: report without location
            context.report(ISSUE, com.android.tools.lint.detector.api.Location.create(context.project.dir), message)
        }
    }

    // -----------------------------------------------------------------------
    // Graph helpers
    // -----------------------------------------------------------------------

    private fun addEdge(resType: ResourceType, from: String, to: String) {
        if (from == to) return   // self-reference – trivial cycle, skip dedup
        graph
            .getOrPut(resType) { mutableMapOf() }
            .getOrPut(from)    { mutableListOf() }
            .add(to)
    }

    private fun addLocation(
        resType: ResourceType,
        name: String,
        context: XmlContext,
        element: Element
    ) {
        locations
            .getOrPut(resType) { mutableMapOf() }
            .putIfAbsent(name, Pair(context, element))
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    /** Strips a resource reference prefix like "@style/" or "?attr/" leaving just the name. */
    private fun stripResourcePrefix(ref: String): String {
        val atIndex = ref.indexOf('/')
        return if (atIndex >= 0) ref.substring(atIndex + 1) else ref
    }

    /**
     * If [text] looks like "@<resType>/name" (matching [resType]) returns "name", else null.
     * Also handles "?attr/name" for attr references.
     */
    private fun extractReference(text: String?, resType: ResourceType): String? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()
        if (!t.startsWith('@') && !t.startsWith('?')) return null

        val slashIdx = t.indexOf('/')
        if (slashIdx < 0) return null

        val typeStr = t.substring(1, slashIdx).trimStart('@', '?')
            .let { if (it.startsWith("android:")) it.substringAfter(':') else it }

        val refType = ResourceType.fromXmlValue(typeStr) ?: return null
        if (refType != resType) return null

        return t.substring(slashIdx + 1).trim().takeIf { it.isNotEmpty() }
    }

    /** Normalise a style name: replace '/' with '.'. */
    private fun normStyleName(name: String): String = name.replace('/', '.')

    private fun tagToResourceType(tag: String): ResourceType? = when (tag) {
        TAG_COLOR         -> ResourceType.COLOR
        "drawable"        -> ResourceType.DRAWABLE
        "string"          -> ResourceType.STRING
        "bool"            -> ResourceType.BOOL
        "integer"         -> ResourceType.INTEGER
        "dimen"           -> ResourceType.DIMEN
        "fraction"        -> ResourceType.FRACTION
        "array",
        "string-array",
        "integer-array"   -> ResourceType.ARRAY
        "plurals"         -> ResourceType.PLURALS
        "attr"            -> ResourceType.ATTR
        "declare-styleable" -> ResourceType.STYLEABLE
        else              -> null
    }

    // -----------------------------------------------------------------------
    // Issue declaration
    // -----------------------------------------------------------------------

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id                  = "ResourceCycle",
            briefDescription    = "Cycle in resource definitions",
            explanation         = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions.
                """,
            category            = Category.CORRECTNESS,
            priority            = 8,
            severity            = Severity.ERROR,
            implementation      = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }
}
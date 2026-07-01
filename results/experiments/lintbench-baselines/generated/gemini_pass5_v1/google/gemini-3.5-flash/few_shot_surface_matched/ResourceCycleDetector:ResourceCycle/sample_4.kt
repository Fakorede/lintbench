package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val references = mutableListOf<ResourceRef>()

    data class ResourceRef(
        val from: String,
        val to: String,
        val location: Location,
        val message: String
    )

    override fun beforeCheckRootProject(context: Context) {
        references.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("style", "dimen", "color", "string", "integer", "bool", "array", "item")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("parent")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        val name = element.getAttribute("name")
        if (name.isNotEmpty()) {
            val currentRes = "@$tagName/$name"
            if (tagName == "style") {
                val parent = element.getAttribute("parent")
                if (parent.isEmpty() && name.contains('.')) {
                    val parentName = name.substringBeforeLast('.')
                    val parentRef = "@style/$parentName"
                    references.add(ResourceRef(currentRes, parentRef, context.getLocation(element), "Style parent cycle: $currentRes -> $parentRef"))
                }
            } else {
                val textContent = element.textContent?.trim() ?: ""
                if (textContent.startsWith("@") && !textContent.startsWith("@android:")) {
                    references.add(ResourceRef(currentRes, textContent, context.getLocation(element), "Resource cycle: $currentRes -> $textContent"))
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        if (attribute.name == "parent" && attribute.ownerElement.tagName == "style") {
            val element = attribute.ownerElement
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val currentRes = "@style/$name"
                val parent = attribute.value
                if (parent.isNotEmpty()) {
                    val parentRef = if (parent.startsWith("@")) parent else "@style/$parent"
                    references.add(ResourceRef(currentRes, parentRef, context.getValueLocation(attribute), "Style parent cycle: $currentRes -> $parentRef"))
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val adj = mutableMapOf<String, MutableList<ResourceRef>>()
        for (ref in references) {
            adj.getOrPut(ref.from) { mutableListOf() }.add(ref)
        }

        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<ResourceRef>()
        val reported = mutableSetOf<String>()

        fun dfs(u: String) {
            state[u] = 1
            val edges = adj[u] ?: emptyList()
            for (ref in edges) {
                val v = ref.to
                path.add(ref)
                val vState = state[v] ?: 0
                if (vState == 1) {
                    val cycleStartIndex = path.indexOfFirst { it.from == v }
                    if (cycleStartIndex != -1) {
                        val cyclePath = path.subList(cycleStartIndex, path.size)
                        val cycleStr = cyclePath.joinToString(" -> ") { it.from } + " -> $v"
                        if (reported.add(cycleStr)) {
                            context.report(ISSUE, ref.location, "Cycle in resource definitions: $cycleStr")
                        }
                    }
                } else if (vState == 0) {
                    dfs(v)
                }
                path.removeAt(path.size - 1)
            }
            state[u] = 2
        }

        for (u in adj.keys) {
            if ((state[u] ?: 0) == 0) {
                dfs(u)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
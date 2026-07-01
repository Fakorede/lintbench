package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
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
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    private val references = mutableMapOf<ResourceUrl, MutableList<ResourceUrl>>()
    private val locations = mutableMapOf<ResourceUrl, Location>()
    private val visiting = mutableSetOf<ResourceUrl>()
    private val visited = mutableSetOf<ResourceUrl>()
    private val path = mutableListOf<ResourceUrl>()

    override fun beforeCheckRootProject(context: Context) {
        references.clear()
        locations.clear()
        visiting.clear()
        visited.clear()
        path.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? =
        Detector.XmlScanner.ALL

    override fun getApplicableAttributes(): Collection<String>? =
        Detector.XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val source = getResourceUrl(element) ?: return
        locations[source] = context.getLocation(element)

        val text = element.textContent?.trim().orEmpty()
        if (text.isNotEmpty()) {
            ResourceUrl.parse(text)?.let { addEdge(source, it) }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        val source = getResourceUrl(owner) ?: return
        locations[source] = context.getLocation(owner)

        ResourceUrl.parse(attribute.value)?.let { addEdge(source, it) }
    }

    override fun afterCheckRootProject(context: Context) {
        for (node in references.keys) {
            if (node !in visited) {
                findCycles(node, context)
            }
        }
    }

    private fun getResourceUrl(element: Element): ResourceUrl? {
        val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return null
        val typeName = element.getAttribute("type").takeIf { it.isNotEmpty() } ?: element.tagName
        return ResourceUrl.create(null, typeName, name, false)
    }

    private fun addEdge(from: ResourceUrl, to: ResourceUrl) {
        references.getOrPut(from) { mutableListOf() }.add(to)
    }

    private fun findCycles(node: ResourceUrl, context: Context) {
        visiting.add(node)
        path.add(node)

        references[node]?.forEach { ref ->
            when {
                ref in visiting -> {
                    val index = path.indexOf(ref)
                    val cycle = path.subList(index, path.size) + ref
                    val message = buildString {
                        append("Cycle in resource definitions: ")
                        append(cycle.joinToString(" -> "))
                    }
                    val location = locations[path[index]] ?: Location.create(context.file)
                    context.report(ISSUE, location, message)
                }
                ref !in visited -> findCycles(ref, context)
            }
        }

        path.removeAt(path.size - 1)
        visiting.remove(node)
        visited.add(node)
    }
}
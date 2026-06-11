package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.*

class ResourceCycleDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                Cycles in resource definitions can lead to runtime exceptions. Ensure that there are no cyclic dependencies between resources.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val TAGS_TO_CHECK = listOf("item", "array", "string-array")
    }

    private var graph: MutableMap<String, MutableList<String>> = mutableMapOf()
    private var visited: MutableSet<String> = mutableSetOf()
    private var recursionStack: MutableSet<String> = mutableSetOf()

    override fun getApplicableElements(): Collection<String>? {
        return TAGS_TO_CHECK
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = context.getResourceName(element)?.toString() ?: return

        if (!graph.containsKey(name)) {
            graph[name] = mutableListOf()
        }
        val references = extractReferences(element)
        for (reference in references) {
            graph[name]?.add(reference)
        }
    }

    override fun visitElementAfter(context: XmlContext, element: Element) {
        val name = context.getResourceName(element)?.toString() ?: return
        if (!visited.contains(name)) {
            if (detectCycle(name)) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Detected a cycle in resource definitions involving $name"
                )
            }
        }
    }

    private fun extractReferences(element: Element): List<String> {
        val references = mutableListOf<String>()
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i)
            if ("name" == attr.nodeName || "item" == attr.nodeName) continue
            val value = attr.nodeValue.trim()
            if (!value.startsWith("@")) continue

            // Extract the resource name from the reference string.
            val resourceName = value.substring(1).split("/").last().trim('.')
            references.add(resourceName)
        }
        return references
    }

    private fun detectCycle(node: String): Boolean {
        visited.add(node)
        recursionStack.add(node)

        for (neighbor in graph[node] ?: mutableListOf()) {
            if (!visited.contains(neighbor)) {
                if (detectCycle(neighbor)) return true
            } else if (recursionStack.contains(neighbor)) {
                return true
            }
        }

        recursionStack.remove(node)
        return false
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}
    override fun visitDocument(context: XmlContext, document: Document) {}
}
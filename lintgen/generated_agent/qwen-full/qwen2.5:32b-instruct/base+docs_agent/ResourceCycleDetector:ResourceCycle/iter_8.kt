package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import java.util.*

/**
 * Detects cycles in resource definitions which can lead to runtime exceptions.
 */
class ResourceCycleDetector : ResourceXmlDetector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                Cycles in resource definitions can cause runtime exceptions. Ensure that there are no circular references between resources.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val TAGS_TO_CHECK = listOf(TAG_STRING, TAG_PLURALS)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitResource(resFile: XmlContext) {
        if (resFile.file.name != null && resFile.rootElement.tagName in TAGS_TO_CHECK) {
            val graph = mutableMapOf<String, MutableList<String>>()
            buildGraph(resFile, graph)
            checkForCycles(graph, resFile)
        }
    }

    private fun buildGraph(file: XmlContext, graph: MutableMap<String, MutableList<String>>) {
        file.lintClient.getFiles(ResourceFolderType.VALUES).forEach { resFile ->
            val content = resFile.content
            if (content.documentElement != null) {
                traverseElement(content.documentElement as Element, graph)
            }
        }
    }

    private fun traverseElement(element: Element?, graph: MutableMap<String, MutableList<String>>) {
        element ?: return

        val name = element.getAttribute(ATTR_NAME)
        if (name.isNotEmpty()) {
            val references = mutableListOf<String>()
            for (i in 0 until element.attributes.length) {
                val attrName = element.attributes.item(i).nodeName
                if (attrName == ATTR_NAME || attrName == ATTR_ID) continue

                val refValue = element.getAttribute(attrName)
                if (refValue.startsWith("@")) {
                    references.add(refValue.substring(1))
                }
            }

            graph[name] = references
        }

        for (i in 0 until element.childNodes.length) {
            traverseElement(element.childNodes.item(i) as? Element, graph)
        }
    }

    private fun checkForCycles(graph: MutableMap<String, MutableList<String>>, resFile: XmlContext) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        for (node in graph.keys) {
            if (!visited.contains(node)) {
                if (isCyclicUtil(node, visited, recursionStack, graph)) {
                    reportIssue(graph, node, resFile)
                }
            }
        }
    }

    private fun isCyclicUtil(
        node: String,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>,
        graph: Map<String, MutableList<String>>
    ): Boolean {
        if (recursionStack.contains(node)) return true
        if (visited.contains(node)) return false

        visited.add(node)
        recursionStack.add(node)

        for (neighbor in graph[node] ?: emptyList()) {
            if (isCyclicUtil(neighbor, visited, recursionStack, graph)) {
                return true
            }
        }

        recursionStack.remove(node)
        return false
    }

    private fun reportIssue(graph: Map<String, MutableList<String>>, node: String, resFile: XmlContext) {
        val issue = ISSUE
        for ((key, value) in graph) {
            if (value.contains(node)) {
                val file = resFile.file
                if (file != null) {
                    val location = Location.create(file).message("Cycle detected involving $node")
                    report(resFile.context, issue, location)
                }
            }
        }
    }

    override fun getApplicableElements(): Set<String> =
        TAGS_TO_CHECK.toSet()
}
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<Resource, MutableList<Reference>>()

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL
    override fun getApplicableAttributes(): Collection<String>? = null
    override fun getApplicableNamespaces(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        processDocument(context, document)
    }

    override fun visitElement(context: XmlContext, element: Element) {}
    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    override fun beforeCheckEachProject(context: Context) {
        graph.clear()
    }

    override fun afterCheckEachProject(context: Context) {
        detectCycles(context)
    }

    private fun processDocument(context: XmlContext, document: Document) {
        val file = context.file
        val folderName = file.parentFile?.name ?: return
        val folderType = ResourceFolderType.getFolderType(folderName) ?: return

        if (folderType == ResourceFolderType.VALUES) {
            val root = document.documentElement ?: return
            if (root.tagName != SdkConstants.TAG_RESOURCES) return

            for (i in 0 until root.childNodes.length) {
                val child = root.childNodes.item(i)
                if (child.nodeType != Node.ELEMENT_NODE) continue

                val element = child as Element
                val name = element.getAttribute(SdkConstants.ATTR_NAME)
                if (name.isBlank()) continue

                val type = ResourceType.fromName(element.tagName) ?: continue
                val from = Resource(type, name)
                val refs = mutableListOf<Reference>()
                collectReferences(context, element, from, refs, parseText = true)
                graph.getOrPut(from) { mutableListOf() }.addAll(refs)
            }
        } else {
            val type = folderType.resourceType ?: return
            val name = file.nameWithoutExtension
            val from = Resource(type, name)
            val refs = mutableListOf<Reference>()
            collectReferences(
                context,
                document.documentElement ?: return,
                from,
                refs,
                parseText = false
            )
            graph.getOrPut(from) { mutableListOf() }.addAll(refs)
        }
    }

    private fun collectReferences(
        context: XmlContext,
        element: Element,
        from: Resource,
        refs: MutableList<Reference>,
        parseText: Boolean
    ) {
        if (element.tagName == SdkConstants.TAG_STYLE) {
            val parent = element.getAttribute(SdkConstants.ATTR_PARENT)
            if (parent.isNotBlank() && !parent.startsWith("?")) {
                val url = ResourceUrl.parse(parent)
                if (url != null && url.packageName == null) {
                    val toType = url.type ?: ResourceType.STYLE
                    val toName = url.name ?: return
                    refs.add(Reference(from, Resource(toType, toName), context.getLocation(element)))
                } else if (url == null && !parent.contains(":")) {
                    refs.add(Reference(from, Resource(ResourceType.STYLE, parent), context.getLocation(element)))
                }
            }
        }

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as Attr
            val prefix = attr.prefix
            if (prefix == "tools" || prefix == "xmlns") continue

            val value = attr.value ?: continue
            if (value.startsWith("?")) continue
            if (!value.startsWith("@")) continue

            val url = ResourceUrl.parse(value) ?: continue
            if (url.packageName != null) continue

            val toType = url.type ?: continue
            val toName = url.name ?: continue
            refs.add(Reference(from, Resource(toType, toName), context.getLocation(attr)))
        }

        if (parseText &&
            element.childNodes.length == 1 &&
            element.firstChild?.nodeType == Node.TEXT_NODE
        ) {
            val text = element.textContent?.trim().orEmpty()
            if (text.startsWith("@") && !text.startsWith("@?")) {
                val url = ResourceUrl.parse(text) ?: return
                if (url.packageName == null) {
                    val toType = url.type ?: return
                    val toName = url.name ?: return
                    refs.add(Reference(from, Resource(toType, toName), context.getLocation(element)))
                }
            }
        }

        for (i in 0 until element.childNodes.length) {
            val child = element.childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectReferences(context, child as Element, from, refs, parseText)
            }
        }
    }

    private fun detectCycles(context: Context) {
        if (graph.isEmpty()) return

        val index = mutableMapOf<Resource, Int>()
        val lowlink = mutableMapOf<Resource, Int>()
        val stack = ArrayDeque<Resource>()
        val onStack = mutableSetOf<Resource>()
        var nextIndex = 0
        val sccs = mutableListOf<List<Resource>>()

        fun strongconnect(v: Resource) {
            index[v] = nextIndex
            lowlink[v] = nextIndex
            nextIndex++
            stack.addLast(v)
            onStack.add(v)

            for (ref in graph[v].orEmpty()) {
                val w = ref.to
                if (w !in graph) continue

                if (w !in index) {
                    strongconnect(w)
                    lowlink[v] = minOf(lowlink.getValue(v), lowlink.getValue(w))
                } else if (onStack.contains(w)) {
                    lowlink[v] = minOf(lowlink.getValue(v), index.getValue(w))
                }
            }

            if (lowlink.getValue(v) == index.getValue(v)) {
                val component = mutableListOf<Resource>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                    if (w == v) break
                }
                if (component.size > 1) {
                    sccs.add(component)
                }
            }
        }

        for (v in graph.keys) {
            if (v !in index) strongconnect(v)
        }

        for ((v, refs) in graph) {
            if (sccs.none { it.contains(v) } && refs.any { it.to == v }) {
                val ref = refs.first { it.to == v }
                val message = "Cycle in resource definitions: ${v.type.getName()}/${v.name} -> itself"
                context.report(ISSUE, ref.location, message)
            }
        }

        for (scc in sccs) {
            reportScc(context, scc)
        }
    }

    private fun reportScc(context: Context, scc: List<Resource>) {
        val sccSet = scc.toSet()
        val start = scc.first()
        val path = mutableListOf<Resource>()
        val pathSet = mutableSetOf<Resource>()

        fun dfs(v: Resource): Boolean {
            path.add(v)
            pathSet.add(v)

            for (ref in graph[v].orEmpty()) {
                val w = ref.to
                if (w !in sccSet) continue

                if (w == start && path.size > 1) {
                    val cycle = (path + w).joinToString(" -> ") {
                        "${it.type.getName()}/${it.name}"
                    }
                    context.report(ISSUE, ref.location, "Cycle in resource definitions: $cycle")
                    return true
                }

                if (w !in pathSet) {
                    if (dfs(w)) return true
                }
            }

            path.removeLast()
            pathSet.remove(v)
            return false
        }

        dfs(start)
    }

    private data class Resource(val type: ResourceType, val name: String)
    private data class Reference(val from: Resource, val to: Resource, val location: Location)

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to runtime exceptions.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}
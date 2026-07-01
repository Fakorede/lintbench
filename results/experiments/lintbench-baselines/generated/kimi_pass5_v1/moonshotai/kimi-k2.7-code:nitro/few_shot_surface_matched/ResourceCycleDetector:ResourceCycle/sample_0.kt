package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_REF
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.TAG_STYLE
import com.android.ide.common.resources.ResourceUrl
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val references = mutableMapOf<ResourceUrl, MutableList<Pair<ResourceUrl, Location>>>()

    override fun beforeCheckRootProject(context: Context) {
        references.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.COLOR
                || folderType == ResourceFolderType.ANIM
                || folderType == ResourceFolderType.ANIMATOR
    }

    override fun getApplicableElements(): Collection<String> = XmlScanner.ALL

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_DRAWABLE, ATTR_SRC, ATTR_COLOR, ATTR_LAYOUT, ATTR_PARENT, ATTR_REF)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val source = getResource(context, element) ?: return

        if (element.tagName == TAG_STYLE && source.type == ResourceType.STYLE) {
            val name = source.name
            val dot = name.lastIndexOf('.')
            if (dot > 0) {
                val parentName = name.substring(0, dot)
                val parent = ResourceUrl.create(ResourceType.STYLE, parentName)
                addReference(source, parent, context.getLocation(element))
            }
        }

        val text = element.textContent?.trim()
        if (!text.isNullOrEmpty() && text.startsWith("@")) {
            ResourceUrl.parse(text)?.let { target ->
                if (target.packageName == null && target.type == source.type) {
                    addReference(source, target, context.getLocation(element))
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val target = ResourceUrl.parse(value) ?: return
        if (target.packageName != null) return

        val owner = attribute.ownerElement ?: return
        val source = getResource(context, owner) ?: return
        val attrName = attribute.localName

        val record = when {
            (attrName == ATTR_DRAWABLE || attrName == ATTR_SRC)
                    && source.type == ResourceType.DRAWABLE
                    && target.type == ResourceType.DRAWABLE -> true
            attrName == ATTR_COLOR
                    && source.type == ResourceType.COLOR
                    && target.type == ResourceType.COLOR -> true
            attrName == ATTR_LAYOUT
                    && source.type == ResourceType.LAYOUT
                    && target.type == ResourceType.LAYOUT -> true
            attrName == ATTR_PARENT
                    && source.type == ResourceType.STYLE
                    && target.type == ResourceType.STYLE -> true
            attrName == ATTR_REF
                    && source.type == ResourceType.ATTR
                    && target.type == ResourceType.ATTR -> true
            else -> false
        }

        if (record) {
            addReference(source, target, context.getLocation(attribute))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        detectCycles(context)
    }

    private fun getResource(context: XmlContext, element: Element): ResourceUrl? {
        val folderType = context.resourceFolderType ?: return null
        val fileName = context.file.name
        val baseName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName

        return if (folderType == ResourceFolderType.VALUES) {
            val type = ResourceType.fromXmlTag(element) ?: return null
            val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return null
            ResourceUrl.create(type, nameAttr.value)
        } else {
            val type = folderType.resourceType ?: return null
            ResourceUrl.create(type, baseName)
        }
    }

    private fun addReference(from: ResourceUrl, to: ResourceUrl, location: Location) {
        references.getOrPut(from) { mutableListOf() }.add(Pair(to, location))
    }

    private fun detectCycles(context: Context) {
        val visiting = mutableSetOf<ResourceUrl>()
        val visited = mutableSetOf<ResourceUrl>()
        val reported = mutableSetOf<ResourceUrl>()
        val stack = mutableListOf<ResourceUrl>()

        fun reportCycle(cycle: List<ResourceUrl>) {
            cycle.forEach { reported.add(it) }
            for (i in cycle.indices) {
                val from = cycle[i]
                val to = cycle[(i + 1) % cycle.size]
                val location = references[from]?.firstOrNull { it.first == to }?.second ?: continue
                context.report(
                    ISSUE,
                    location,
                    "Cycle in resource definitions: $from -> $to"
                )
            }
        }

        fun dfs(node: ResourceUrl) {
            if (node in visiting) {
                val index = stack.indexOf(node)
                if (index != -1) {
                    reportCycle(stack.subList(index, stack.size).toList())
                }
                return
            }
            if (node in visited || node in reported) {
                return
            }

            visiting.add(node)
            stack.add(node)

            references[node]?.forEach { (target, _) ->
                if (target !in reported) {
                    dfs(target)
                }
            }

            stack.removeAt(stack.size - 1)
            visiting.remove(node)
            visited.add(node)
        }

        for (source in references.keys.toList()) {
            if (source !in visited && source !in reported) {
                dfs(source)
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
            priority = 10,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }
}
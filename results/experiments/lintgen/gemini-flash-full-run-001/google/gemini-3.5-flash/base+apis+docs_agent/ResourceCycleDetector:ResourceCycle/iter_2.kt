package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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
import java.util.EnumSet
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : Detector(), XmlScanner {

    private class ResourceInfo(
        val key: String,
        val type: String,
        val name: String,
        val location: Location
    ) {
        val references = mutableListOf<Reference>()
    }

    private class Reference(
        val targetKey: String,
        val location: Location
    )

    private val resources = mutableMapOf<String, ResourceInfo>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun beforeCheckEachProject(context: Context) {
        resources.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        val root = document.documentElement ?: return

        if (folderType == ResourceFolderType.VALUES) {
            var child = root.firstChild
            while (child != null) {
                if (child is Element) {
                    val tagName = child.tagName
                    var type = tagName
                    if (type == "item") {
                        type = child.getAttribute("type") ?: ""
                    }
                    val name = child.getAttribute("name")
                    if (!type.isNullOrEmpty() && !name.isNullOrEmpty()) {
                        val key = "@$type/$name"
                        val resInfo = ResourceInfo(key, type, name, context.getLocation(child))
                        resources[key] = resInfo

                        if (tagName == "style") {
                            val parent = child.getAttribute("parent")
                            if (!parent.isNullOrEmpty()) {
                                val parentKey = if (parent.startsWith("@")) {
                                    if (parent.startsWith("@style/")) parent else null
                                } else if (parent.contains("android:")) {
                                    null
                                } else {
                                    "@style/$parent"
                                }
                                if (parentKey != null) {
                                    val parentAttr = child.getAttributeNode("parent")
                                    val loc = if (parentAttr != null) context.getLocation(parentAttr) else context.getLocation(child)
                                    resInfo.references.add(Reference(parentKey, loc))
                                }
                            } else if (name.contains(".")) {
                                val parentName = name.substringBeforeLast(".")
                                if (parentName.isNotEmpty()) {
                                    val parentKey = "@style/$parentName"
                                    val nameAttr = child.getAttributeNode("name")
                                    val loc = if (nameAttr != null) context.getLocation(nameAttr) else context.getLocation(child)
                                    resInfo.references.add(Reference(parentKey, loc))
                                }
                            }
                        }

                        scanForReferences(child, resInfo, context)
                    }
                }
                child = child.nextSibling
            }
        } else {
            val type = folderType.getName()
            val name = context.file.name.substringBefore('.')
            val key = "@$type/$name"
            val resInfo = ResourceInfo(key, type, name, context.getLocation(root))
            resources[key] = resInfo
            scanForReferences(root, resInfo, context)
        }
    }

    private fun scanForReferences(node: Node, resInfo: ResourceInfo, context: XmlContext) {
        if (node is Element) {
            val attributes = node.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as Attr
                if (attr.localName == "name" && node.parentNode == node.ownerDocument.documentElement) {
                    continue
                }
                if (attr.localName == "parent" && node.tagName == "style") {
                    continue
                }
                val value = attr.nodeValue
                if (!value.isNullOrEmpty()) {
                    extractReferences(value, resInfo, context, attr)
                }
            }

            var child = node.firstChild
            while (child != null) {
                scanForReferences(child, resInfo, context)
                child = child.nextSibling
            }
        } else if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            val value = node.nodeValue
            if (!value.isNullOrBlank()) {
                extractReferences(value, resInfo, context, node)
            }
        }
    }

    private fun extractReferences(text: String, resInfo: ResourceInfo, context: XmlContext, node: Node) {
        val matcher = RESOURCE_PATTERN.matcher(text)
        while (matcher.find()) {
            val matchedText = matcher.group()
            if (matchedText.startsWith("@
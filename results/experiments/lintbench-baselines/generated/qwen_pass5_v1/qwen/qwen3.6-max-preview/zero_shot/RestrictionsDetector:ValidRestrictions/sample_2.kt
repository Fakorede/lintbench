package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed. " +
                "The root element must be <restrictions> and it must contain <restriction> elements " +
                "with required attributes android:key, android:title, and android:restrictionType.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private val VALID_TYPES = setOf(
            "bool", "string", "integer", "choice", "multi-select", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? = listOf("restrictions", "restriction")

    override fun visitElement(context: XmlContext, element: Element) {
        val doc = element.ownerDocument ?: return
        if (doc.documentElement.tagName != "restrictions") return

        when (element.tagName) {
            "restrictions" -> validateRoot(context, element)
            "restriction" -> validateRestriction(context, element)
        }
    }

    private fun validateRoot(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName != "restriction") {
                context.report(
                    ISSUE, child, context.getLocation(child),
                    "Invalid element `<${child.nodeName}>` in restrictions file. Only `<restriction>` is allowed."
                )
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val androidNs = SdkConstants.ANDROID_URI
        val key = element.getAttributeNS(androidNs, "key")
        val title = element.getAttributeNS(androidNs, "title")
        val type = element.getAttributeNS(androidNs, "restrictionType")

        if (key.isNullOrEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:key`")
        }
        if (title.isNullOrEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:title`")
        }

        val hasValidType = type.isNotEmpty() && VALID_TYPES.contains(type)
        if (type.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:restrictionType`")
        } else if (!hasValidType) {
            context.report(
                ISSUE, element, context.getLocation(element),
                "Invalid `android:restrictionType` value `$type`. Must be one of: ${VALID_TYPES.joinToString()}"
            )
        }

        if (type == "choice" || type == "multi-select") {
            val entries = element.getAttributeNS(androidNs, "entries")
            val entryValues = element.getAttributeNS(androidNs, "entryValues")
            if (entries.isNullOrEmpty()) {
                context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:entries` for type `$type`")
            }
            if (entryValues.isNullOrEmpty()) {
                context.report(ISSUE, element, context.getLocation(element), "Missing required attribute `android:entryValues` for type `$type`")
            }
        }

        val hasDirectElementChild = (0 until element.childNodes.length).any {
            element.childNodes.item(it).nodeType == Node.ELEMENT_NODE
        }

        if (hasDirectElementChild && hasValidType && type != "bundle" && type != "bundle_array") {
            context.report(
                ISSUE, element, context.getLocation(element),
                "Nested `<restriction>` elements are only allowed for `bundle` or `bundle_array` types."
            )
        }
    }
}
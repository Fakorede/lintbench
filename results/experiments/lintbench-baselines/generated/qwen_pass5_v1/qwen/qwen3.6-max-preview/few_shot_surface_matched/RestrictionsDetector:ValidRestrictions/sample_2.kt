package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(context: XmlContext): Boolean {
        return context.resourceFolderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") return

        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                if (element.tagName == "restriction") {
                    checkRestriction(context, element)
                }
            }
            child = child.nextSibling
        }
    }

    private fun checkRestriction(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(ANDROID_URI, "key")
        if (keyAttr == null) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing required attribute `android:key`")
        }

        val titleAttr = element.getAttributeNodeNS(ANDROID_URI, "title")
        if (titleAttr == null) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing required attribute `android:title`")
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")
        if (typeAttr == null) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing required attribute `android:restrictionType`")
            return
        }

        val type = typeAttr.value
        val validTypes = setOf("bool", "choice", "multi-select", "string", "integer", "hidden", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(ISSUE, typeAttr, context.getValueLocation(typeAttr),
                "Invalid restriction type `$type`. Must be one of: ${validTypes.joinToString(", ")}")
        }

        if (type == "choice" || type == "multi-select") {
            var hasEntry = false
            var hasEntryValue = false
            var node = element.firstChild
            while (node != null) {
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val tag = (node as Element).tagName
                    if (tag == "entry") hasEntry = true
                    if (tag == "entry-value") hasEntryValue = true
                }
                node = node.nextSibling
            }
            if (!hasEntry) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Missing `<entry>` elements for restriction type `$type`")
            }
            if (!hasEntryValue) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Missing `<entry-value>` elements for restriction type `$type`")
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            RestrictionsDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed according to the RestrictionsManager specification. The root element must be <restrictions>, containing <restriction> children with required attributes android:key, android:title, and android:restrictionType.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private val VALID_RESTRICTION_TYPES = setOf(
            "bool", "choice", "multi_select", "integer", "string", "hidden", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.XML

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") return

        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                if (child.nodeName == "restriction") {
                    validateRestriction(context, child as Element)
                } else {
                    context.report(
                        ISSUE,
                        context.getLocation(child),
                        "Invalid child element <${child.nodeName}>. Only <restriction> is allowed inside <restrictions>."
                    )
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(ANDROID_URI, "key")
        val title = element.getAttributeNS(ANDROID_URI, "title")
        val type = element.getAttributeNS(ANDROID_URI, "restrictionType")

        if (key.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute android:key")
        }
        if (title.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute android:title")
        }
        if (type.isEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute android:restrictionType")
        } else if (type !in VALID_RESTRICTION_TYPES) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Invalid android:restrictionType '$type'. Must be one of: ${VALID_RESTRICTION_TYPES.joinToString(", ")}"
            )
        }

        if (type == "choice" || type == "multi_select") {
            if (element.getAttributeNS(ANDROID_URI, "entries").isEmpty()) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Missing required attribute android:entries for restrictionType '$type'"
                )
            }
            if (element.getAttributeNS(ANDROID_URI, "entryValues").isEmpty()) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Missing required attribute android:entryValues for restrictionType '$type'"
                )
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                if (child.nodeName == "restriction") {
                    validateRestriction(context, child as Element)
                } else {
                    context.report(
                        ISSUE,
                        context.getLocation(child),
                        "Invalid child element <${child.nodeName}> inside <restriction>. Only nested <restriction> elements are allowed."
                    )
                }
            }
        }
    }
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

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
            explanation = "Ensures that an application's restrictions XML file is properly formed and contains valid elements and attributes.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != "restrictions") {
            return
        }

        val rootChildren = root.childNodes
        for (i in 0 until rootChildren.length) {
            val child = rootChildren.item(i)
            if (child is org.w3c.dom.Element) {
                if (child.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Only `<restriction>` elements are allowed under `<restrictions>`"
                    )
                } else {
                    validateRestriction(context, child)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: org.w3c.dom.Element) {
        val hasKey = element.hasAttributeNS(ANDROID_URI, "key")
        if (!hasKey) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:key` attribute"
            )
        }

        val hasType = element.hasAttributeNS(ANDROID_URI, "restrictionType")
        if (!hasType) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `android:restrictionType` attribute"
            )
            return
        }

        val type = element.getAttributeNS(ANDROID_URI, "restrictionType")
        val validTypes = setOf(
            "bool", "integer", "string", "select-one", "select-multiple", "hidden", "bundle", "bundle-array"
        )

        if (type !in validTypes) {
            val typeNode = element.getAttributeNodeNS(ANDROID_URI, "restrictionType")
            val location = if (typeNode != null) context.getLocation(typeNode) else context.getNameLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "Invalid `android:restrictionType` `$type`"
            )
            return
        }

        if (type == "select-one" || type == "select-multiple") {
            val hasEntries = element.hasAttributeNS(ANDROID_URI, "entries")
            val hasEntryValues = element.hasAttributeNS(ANDROID_URI, "entryValues")
            if (!hasEntries) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Type `$type` requires `android:entries` attribute"
                )
            }
            if (!hasEntryValues) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Type `$type` requires `android:entryValues` attribute"
                )
            }
        }

        val childNodes = element.childNodes
        var hasChildren = false
        val childKeys = mutableSetOf<String>()

        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is org.w3c.dom.Element) {
                hasChildren = true
                if (type != "bundle" && type != "bundle-array") {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Restriction of type `$type` cannot have nested restrictions"
                    )
                } else if (child.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "Only `<restriction>` elements are allowed inside `<restriction>`"
                    )
                } else {
                    if (type == "bundle-array") {
                        val childType = child.getAttributeNS(ANDROID_URI, "restrictionType")
                        if (childType != "bundle") {
                            context.report(
                                ISSUE,
                                child,
                                context.getNameLocation(child),
                                "Restrictions of type `bundle-array` can only contain children of type `bundle`"
                            )
                        }
                    }
                    validateRestriction(context, child)

                    val childKey = child.getAttributeNS(ANDROID_URI, "key")
                    if (childKey.isNotEmpty()) {
                        if (!childKeys.add(childKey)) {
                            val keyNode = child.getAttributeNodeNS(ANDROID_URI, "key")
                            val location = if (keyNode != null) context.getLocation(keyNode) else context.getNameLocation(child)
                            context.report(
                                ISSUE,
                                child,
                                location,
                                "Duplicate key `$childKey`"
                            )
                        }
                    }
                }
            }
        }

        if ((type == "bundle" || type == "bundle-array") && !hasChildren) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Restriction of type `$type` must have at least one nested `<restriction>`"
            )
        }
    }
}
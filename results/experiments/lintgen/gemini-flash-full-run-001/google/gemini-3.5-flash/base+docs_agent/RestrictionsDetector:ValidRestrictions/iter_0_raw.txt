package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class RestrictionsDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = """
                Ensures that an application's restrictions XML file is properly formed, \
                containing valid restriction types, required attributes, and correct nesting.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("restrictions")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.XML) {
            return
        }

        val keys = mutableSetOf<String>()
        validateElement(context, element, keys)
    }

    private fun validateElement(context: XmlContext, element: Element, keys: MutableSet<String>) {
        val tagName = element.tagName
        if (tagName == "restrictions") {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    if (child.tagName != "restriction") {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Only `<restriction>` elements are allowed under `<restrictions>`"
                        )
                    } else {
                        validateElement(context, child, keys)
                    }
                }
            }
        } else if (tagName == "restriction") {
            // 1. Check key
            val keyAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "key")
            if (keyAttr == null || keyAttr.value.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:key` attribute"
                )
            } else {
                val key = keyAttr.value
                if (!keys.add(key)) {
                    context.report(
                        ISSUE,
                        keyAttr,
                        context.getValueLocation(keyAttr),
                        "Duplicate restriction key '$key'"
                    )
                }
            }

            // 2. Check restrictionType
            val typeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "restrictionType")
            val type = typeAttr?.value
            if (type.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `android:restrictionType` attribute"
                )
            } else {
                val validTypes = setOf(
                    "boolean", "integer", "string", "choice",
                    "multi-select", "hidden", "bundle", "bundle_array"
                )
                if (type !in validTypes) {
                    context.report(
                        ISSUE,
                        typeAttr,
                        context.getValueLocation(typeAttr),
                        "Invalid `android:restrictionType` '$type'"
                    )
                } else {
                    // 3. Check title (required for all except hidden)
                    if (type != "hidden") {
                        val titleAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "title")
                        if (titleAttr == null || titleAttr.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "Missing `android:title` attribute"
                            )
                        }
                    }

                    // 4. Check entries and entryValues for choice / multi-select
                    if (type == "choice" || type == "multi-select") {
                        val entriesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entries")
                        val entryValuesAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entryValues")
                        if (entriesAttr == null || entriesAttr.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "Missing `android:entries` attribute for restriction type '$type'"
                            )
                        }
                        if (entryValuesAttr == null || entryValuesAttr.value.isEmpty()) {
                            context.report(
                                ISSUE,
                                element,
                                context.getNameLocation(element),
                                "Missing `android:entryValues` attribute for restriction type '$type'"
                            )
                        }
                    }

                    // 5. Check children
                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val child = childNodes.item(i)
                        if (child is Element) {
                            if (child.tagName == "restriction") {
                                if (type != "bundle" && type != "bundle_array") {
                                    context.report(
                                        ISSUE,
                                        child,
                                        context.getLocation(child),
                                        "Nested restrictions are only supported for `bundle` or `bundle_array` types"
                                    )
                                } else {
                                    validateElement(context, child, keys)
                                }
                            } else {
                                context.report(
                                    ISSUE,
                                    child,
                                    context.getLocation(child),
                                    "Only `<restriction>` elements are allowed"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
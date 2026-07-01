package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an application's restrictions XML file is properly formed.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("restrictions")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.ownerDocument.documentElement != element) {
            return
        }
        validateRestrictions(context, element)
    }

    private fun validateRestrictions(context: XmlContext, parent: Element) {
        val childNodes = parent.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                if (node.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Expected `<restriction>` element, found `<${node.tagName}>`"
                    )
                } else {
                    validateRestriction(context, node)
                }
            }
        }
    }

    private fun validateRestriction(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(SdkConstants.ANDROID_URI, "key")
        val restrictionType = element.getAttributeNS(SdkConstants.ANDROID_URI, "restrictionType")

        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "key") || key.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:key` attribute"
            )
        }

        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "restrictionType")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `android:restrictionType` attribute"
            )
        } else {
            val validTypes = setOf(
                "string",
                "bool",
                "integer",
                "choice",
                "multi-select",
                "hidden",
                "bundle",
                "bundle_array"
            )
            if (restrictionType !in validTypes) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Invalid `android:restrictionType` value: `$restrictionType`"
                )
            } else {
                if (restrictionType == "choice" || restrictionType == "multi-select") {
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "entries")) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Missing `android:entries` attribute for type `$restrictionType`"
                        )
                    }
                    if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, "entryValues")) {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Missing `android:entryValues` attribute for type `$restrictionType`"
                        )
                    }
                }

                if (restrictionType == "bundle" || restrictionType == "bundle_array") {
                    validateRestrictions(context, element)
                } else {
                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val node = childNodes.item(i)
                        if (node is Element) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Restriction of type `$restrictionType` cannot have child elements"
                            )
                        }
                    }
                }
            }
        }
    }
}
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
                Ensures that an application's restrictions XML file is properly formed. \
                Each restriction must have a valid key, restriction type, and appropriate attributes.
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("restrictions")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.ownerDocument.documentElement == element) {
            validateRestrictions(context, element)
        }
    }

    private fun validateRestrictions(context: XmlContext, restrictionsElement: Element) {
        val childNodes = restrictionsElement.childNodes
        var hasChildren = false
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                hasChildren = true
                if (node.tagName != "restriction") {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Only `<restriction>` elements are allowed under `<restrictions>`"
                    )
                } else {
                    validateRestrictionElement(context, node)
                }
            }
        }
        if (!hasChildren) {
            context.report(
                ISSUE,
                restrictionsElement,
                context.getLocation(restrictionsElement),
                "`<restrictions>` element should have at least one child `<restriction>`"
            )
        }
    }

    private fun validateRestrictionElement(context: XmlContext, element: Element) {
        val keyAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "key")
        if (keyAttr == null || keyAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:key`"
            )
        }

        val typeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "restrictionType")
        if (typeAttr == null || typeAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing required attribute `android:restrictionType`"
            )
            return
        }

        val type = typeAttr.value
        val validTypes = setOf("bool", "string", "integer", "choice", "multi-select", "hidden", "bundle", "bundle_array")
        if (type !in validTypes) {
            context.report(
                ISSUE,
                typeAttr,
                context.getLocation(typeAttr),
                "Invalid restriction type `$type`"
            )
            return
        }

        if (type == "choice" || type == "multi-select") {
            val entries = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entries")
            val entryValues = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "entryValues")
            if (entries == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$type` requires `android:entries` attribute"
                )
            }
            if (entryValues == null) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction type `$type` requires `android:entryValues` attribute"
                )
            }
        }

        val childNodes = element.childNodes
        val hasChildren = (0 until childNodes.length).any { childNodes.item(it) is Element }

        if (type == "bundle" || type == "bundle_array") {
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    if (child.tagName != "restriction") {
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(child),
                            "Only `<restriction>` elements are allowed under `<restriction>` of type `$type`"
                        )
                    } else {
                        validateRestrictionElement(context, child)
                    }
                }
            }
        } else {
            if (hasChildren) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Restriction of type `$type` cannot have nested restrictions"
                )
            }
        }
    }
}
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
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : Detector(), XmlScanner {
    companion object {
        const val MAX_NESTING_DEPTH = 2
        const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 100

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidRestrictions",
            briefDescription = "Invalid Restrictions Descriptor",
            explanation = "Ensures that an applications restrictions XML file is properly formed.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                RestrictionsDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val VALID_TYPES = setOf(
            "bool", "choice", "multi-select", "integer", "string", "hidden", "bundle", "bundle_array"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? = listOf("restriction")

    override fun visitElement(context: XmlContext, element: Element) {
        val key = element.getAttributeNS(SdkConstants.ANDROID_URI, "key")
        val title = element.getAttributeNS(SdkConstants.ANDROID_URI, "title")
        val type = element.getAttributeNS(SdkConstants.ANDROID_URI, "restrictionType")

        if (key.isNullOrEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:key`")
        }
        if (title.isNullOrEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:title`")
        }
        if (type.isNullOrEmpty()) {
            context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:restrictionType`")
            return
        }

        if (type !in VALID_TYPES) {
            val typeNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "restrictionType")
            context.report(
                ISSUE,
                context.getLocation(typeNode ?: element),
                "Invalid `android:restrictionType`: `$type`. Must be one of: ${VALID_TYPES.joinToString()}"
            )
        }

        if (type == "choice" || type == "multi-select") {
            val entries = element.getAttributeNS(SdkConstants.ANDROID_URI, "entries")
            val entryValues = element.getAttributeNS(SdkConstants.ANDROID_URI, "entryValues")
            if (entries.isNullOrEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:entries` for type `$type`")
            }
            if (entryValues.isNullOrEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Missing required attribute `android:entryValues` for type `$type`")
            }
        }

        var depth = 0
        var parent: Node? = element.parentNode
        while (parent != null) {
            if (parent is Element && parent.tagName == "restriction") {
                depth++
            }
            parent = parent.parentNode
        }
        if (depth > MAX_NESTING_DEPTH) {
            context.report(ISSUE, context.getLocation(element), "Nesting depth exceeds maximum of $MAX_NESTING_DEPTH")
        }
    }
}
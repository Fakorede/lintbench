package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class RestrictionsDetector : Detector(), XmlScanner {
    companion object {
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
    }
}
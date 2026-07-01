package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val xmlContext = context as? XmlContext ?: return
        val document = xmlContext.document ?: return
        val manifest = document.documentElement ?: return
        val application = getFirstChild(manifest, "application") ?: return

        val minSdk = xmlContext.mainProject.minSdkVersion ?: 1
        val requiresCategory = minSdk < 30

        var service = getFirstChild(application, "service")
        while (service != null) {
            var metaData = getFirstChild(service, "meta-data")
            while (metaData != null) {
                val name = metaData.getAttributeNS(ANDROID_NS, "name")
                val value = metaData.getAttributeNS(ANDROID_NS, "value")
                if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                    checkForConfigurationActivity(xmlContext, application, metaData, requiresCategory)
                }
                metaData = getNextSibling(metaData, "meta-data")
            }
            service = getNextSibling(service, "service")
        }
    }

    private fun checkForConfigurationActivity(
        context: XmlContext,
        application: org.w3c.dom.Element,
        metaDataElement: org.w3c.dom.Element,
        requiresCategory: Boolean
    ) {
        var activity = getFirstChild(application, "activity")
        var found = false
        while (activity != null) {
            var intentFilter = getFirstChild(activity, "intent-filter")
            while (intentFilter != null) {
                var hasAction = false
                var hasCategory = !requiresCategory

                var action = getFirstChild(intentFilter, "action")
                while (action != null) {
                    if (action.getAttributeNS(ANDROID_NS, "name") == "WATCH_FACE_EDITOR") {
                        hasAction = true
                    }
                    action = getNextSibling(action, "action")
                }

                if (requiresCategory) {
                    var category = getFirstChild(intentFilter, "category")
                    while (category != null) {
                        if (category.getAttributeNS(ANDROID_NS, "name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                        }
                        category = getNextSibling(category, "category")
                    }
                }

                if (hasAction && hasCategory) {
                    found = true
                    break
                }
                intentFilter = getNextSibling(intentFilter, "intent-filter")
            }
            if (found) break
            activity = getNextSibling(activity, "activity")
        }

        if (!found) {
            val suffix = if (requiresCategory) " and category com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" else ""
            context.report(
                ISSUE,
                metaDataElement,
                context.getLocation(metaDataElement),
                "Duplicate watch face configuration activities found. " +
                "If a watch face service defines wearableConfigurationAction metadata with value WATCH_FACE_EDITOR, " +
                "there must be an activity in the same package with an intent filter for WATCH_FACE_EDITOR$suffix."
            )
        }
    }

    private fun getFirstChild(parent: org.w3c.dom.Element, tagName: String): org.w3c.dom.Element? {
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE && child.localName == tagName) {
                return child as org.w3c.dom.Element
            }
            child = child.nextSibling
        }
        return null
    }

    private fun getNextSibling(current: org.w3c.dom.Element, tagName: String): org.w3c.dom.Element? {
        var sibling = current.nextSibling
        while (sibling != null) {
            if (sibling.nodeType == org.w3c.dom.Node.ELEMENT_NODE && sibling.localName == tagName) {
                return sibling as org.w3c.dom.Element
            }
            sibling = sibling.nextSibling
        }
        return null
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata, " +
                "with the value WATCH_FACE_EDITOR, there should be an activity in the same package, " +
                "which has an intent filter for WATCH_FACE_EDITOR (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION " +
                "if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
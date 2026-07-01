package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val document = context.mainProject.mergedManifest ?: return
        val root = document.documentElement ?: return
        val application = root.subElements("application").firstOrNull() ?: return

        val services = application.subElements("service")
        val activities = application.subElements("activity") + application.subElements("activity-alias")

        val actionName = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        val categoryName = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        val metadataName = "com.google.android.wearable.watchface.wearableConfigurationAction"

        val matchingServices = services.filter { service ->
            var hasMeta = false
            for (meta in service.subElements("meta-data")) {
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == metadataName && (value == actionName || value == "WATCH_FACE_EDITOR")) {
                    hasMeta = true
                }
            }
            hasMeta
        }

        val minSdkVersion = context.project.minSdkVersion.featureLevel
        val requireCategory = minSdkVersion < 30

        val matchingActivities = activities.filter { activity ->
            matchesConfiguration(activity, actionName, categoryName, requireCategory = false)
        }

        val validMatchingActivities = activities.filter { activity ->
            matchesConfiguration(activity, actionName, categoryName, requireCategory = requireCategory)
        }

        val manifestFile = context.mainProject.manifestFiles.firstOrNull()

        fun report(element: Element, message: String) {
            val location = if (manifestFile != null) {
                context.client.xmlParser.getLocation(manifestFile, element)
            } else {
                context.getLocation(context.mainProject)
            }
            context.report(ISSUE, element, location, message)
        }

        // 1. Duplicate watch face configuration activities
        if (matchingActivities.size > 1) {
            for (activity in matchingActivities) {
                report(activity, "Duplicate watch face configuration activities found")
            }
            return
        }

        // 2. Service defines metadata but no activity is found
        if (matchingServices.isNotEmpty() && matchingActivities.isEmpty()) {
            for (service in matchingServices) {
                report(service, "A watch face service defines `wearableConfigurationAction` metadata, but no matching configuration activity was found")
            }
            return
        }

        // 3. Activity is found but no service defines metadata
        if (matchingServices.isEmpty() && matchingActivities.isNotEmpty()) {
            for (activity in matchingActivities) {
                report(activity, "A watch face configuration activity was found, but no watch face service defines the `wearableConfigurationAction` metadata")
            }
            return
        }

        // 4. Activity is found but category is missing (when minSdkVersion < 30)
        if (matchingServices.isNotEmpty() && matchingActivities.isNotEmpty() && validMatchingActivities.isEmpty()) {
            for (activity in matchingActivities) {
                report(activity, "The configuration activity must define the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category when minSdkVersion is less than 30")
            }
        }
    }

    private fun Element.subElements(tagName: String): List<Element> {
        val list = mutableListOf<Element>()
        val children = this.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) {
                list.add(node)
            }
        }
        return list
    }

    private fun matchesConfiguration(
        activity: Element,
        actionName: String,
        categoryName: String,
        requireCategory: Boolean
    ): Boolean {
        for (filter in activity.subElements("intent-filter")) {
            var hasAction = false
            var hasCategory = false
            for (action in filter.subElements("action")) {
                if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == actionName) {
                    hasAction = true
                }
            }
            if (requireCategory) {
                for (category in filter.subElements("category")) {
                    if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == categoryName) {
                        hasCategory = true
                    }
                }
            } else {
                hasCategory = true
            }
            if (hasAction && hasCategory) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}
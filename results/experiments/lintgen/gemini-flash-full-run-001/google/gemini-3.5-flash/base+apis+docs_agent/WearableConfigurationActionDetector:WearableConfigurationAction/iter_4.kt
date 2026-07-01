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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val services = mutableListOf<Element>()
        val serviceList = root.getElementsByTagName(SdkConstants.TAG_SERVICE)
        for (i in 0 until serviceList.length) {
            services.add(serviceList.item(i) as Element)
        }

        val activities = mutableListOf<Element>()
        val activityList = root.getElementsByTagName(SdkConstants.TAG_ACTIVITY)
        for (i in 0 until activityList.length) {
            activities.add(activityList.item(i) as Element)
        }

        val minSdkVersion = context.project.minSdkVersion?.apiLevel ?: 1

        val configMetadatas = mutableListOf<Element>()
        for (service in services) {
            val metaDataList = service.getElementsByTagName(SdkConstants.TAG_META_DATA)
            for (i in 0 until metaDataList.length) {
                val metaData = metaDataList.item(i) as Element
                if (isWearableConfigMetadata(metaData)) {
                    configMetadatas.add(metaData)
                }
            }
        }

        val configActivities = mutableListOf<Element>()
        for (activity in activities) {
            if (isConfigActivity(activity)) {
                configActivities.add(activity)
            }
        }

        val hasMatchingActivity = configActivities.any { matchesActivity(it, minSdkVersion) }

        if (configMetadatas.isNotEmpty() && !hasMatchingActivity) {
            for (metaData in configMetadatas) {
                val message = if (minSdkVersion < 30) {
                    "To support wearable configuration, there must be an activity with an intent filter for " +
                            "action 'com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR' " +
                            "and category 'com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION'."
                } else {
                    "To support wearable configuration, there must be an activity with an intent filter for " +
                            "action 'com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR'."
                }
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    message
                )
            }
        }

        if (configActivities.isNotEmpty() && configMetadatas.isEmpty()) {
            for (activity in configActivities) {
                val message = "An activity is configured as a watch face editor, but no watch face service defines the " +
                        "corresponding 'com.google.android.wearable.watchface.wearableConfigurationAction' metadata."
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    message
                )
            }
        }
    }

    private fun Element.getAndroidAttribute(localName: String): String {
        val nsValue = getAttributeNS(SdkConstants.ANDROID_URI, localName)
        if (nsValue.isNotEmpty()) {
            return nsValue
        }
        val prefixValue = getAttribute("android:$localName")
        if (prefixValue.isNotEmpty()) {
            return prefixValue
        }
        return ""
    }

    private fun isWearableConfigMetadata(element: Element): Boolean {
        val name = element.getAndroidAttribute(SdkConstants.ATTR_NAME)
        val value = element.getAndroidAttribute(SdkConstants.ATTR_VALUE)

        val nameMatch = name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                name == "wearableConfigurationAction"
        val valueMatch = value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                value == "WATCH_FACE_EDITOR"

        return nameMatch && valueMatch
    }

    private fun isConfigActivity(activity: Element): Boolean {
        val intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                val actionName = action.getAndroidAttribute(SdkConstants.ATTR_NAME)
                if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                    actionName == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun matchesActivity(activity: Element, minSdkVersion: Int): Boolean {
        val intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            var hasAction = false
            var hasCategory = false

            val actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                val actionName = action.getAndroidAttribute(SdkConstants.ATTR_NAME)
                if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                    actionName == "WATCH_FACE_EDITOR") {
                    hasAction = true
                }
            }

            val categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY)
            for (j in 0 until categories.length) {
                val category = categories.item(j) as Element
                val categoryName = category.getAndroidAttribute(SdkConstants.ATTR_NAME)
                if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" ||
                    categoryName == "WEARABLE_CONFIGURATION") {
                    hasCategory = true
                }
            }

            if (hasAction) {
                if (minSdkVersion < 30) {
                    if (hasCategory) {
                        return true
                    }
                } else {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
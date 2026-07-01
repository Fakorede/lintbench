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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_APPLICATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != SdkConstants.TAG_APPLICATION) return

        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                when (child.tagName) {
                    SdkConstants.TAG_SERVICE -> services.add(child)
                    SdkConstants.TAG_ACTIVITY -> activities.add(child)
                }
            }
            child = child.nextSibling
        }

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        val configMetadatas = mutableListOf<Element>()
        for (service in services) {
            var serviceChild = service.firstChild
            while (serviceChild != null) {
                if (serviceChild is Element && serviceChild.tagName == SdkConstants.TAG_META_DATA) {
                    if (isWearableConfigMetadata(serviceChild)) {
                        configMetadatas.add(serviceChild)
                    }
                }
                serviceChild = serviceChild.nextSibling
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

    private fun isWearableConfigMetadata(element: Element): Boolean {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        val value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)

        val nameMatch = name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                name == "wearableConfigurationAction"
        val valueMatch = value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                value == "WATCH_FACE_EDITOR"

        return nameMatch && valueMatch
    }

    private fun isConfigActivity(activity: Element): Boolean {
        var child = activity.firstChild
        while (child != null) {
            if (child is Element && child.tagName == SdkConstants.TAG_INTENT_FILTER) {
                var filterChild = child.firstChild
                while (filterChild != null) {
                    if (filterChild is Element && filterChild.tagName == SdkConstants.TAG_ACTION) {
                        val actionName = filterChild.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                            actionName == "WATCH_FACE_EDITOR") {
                            return true
                        }
                    }
                    filterChild = filterChild.nextSibling
                }
            }
            child = child.nextSibling
        }
        return false
    }

    private fun matchesActivity(activity: Element, minSdkVersion: Int): Boolean {
        var child = activity.firstChild
        while (child != null) {
            if (child is Element && child.tagName == SdkConstants.TAG_INTENT_FILTER) {
                var hasAction = false
                var hasCategory = false

                var filterChild = child.firstChild
                while (filterChild != null) {
                    if (filterChild is Element) {
                        if (filterChild.tagName == SdkConstants.TAG_ACTION) {
                            val actionName = filterChild.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                            if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR" ||
                                actionName == "WATCH_FACE_EDITOR") {
                                hasAction = true
                            }
                        } else if (filterChild.tagName == SdkConstants.TAG_CATEGORY) {
                            val categoryName = filterChild.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                            if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" ||
                                categoryName == "WEARABLE_CONFIGURATION") {
                                hasCategory = true
                            }
                        }
                    }
                    filterChild = filterChild.nextSibling
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
            child = child.nextSibling
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
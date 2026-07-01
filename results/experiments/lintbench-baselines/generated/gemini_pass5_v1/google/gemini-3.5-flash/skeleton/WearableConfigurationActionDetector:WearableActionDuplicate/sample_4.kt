package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Location

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "A watch face service that defines a wearableConfigurationAction metadata " +
                    "representing an editor must have exactly one corresponding configuration activity " +
                    "with the WATCH_FACE_EDITOR intent filter in the same package. If minSdkVersion " +
                    "is less than 30, this activity must also include the WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val METADATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun checkMergedProject(context: Context) {
        val manifestFile = context.mainProject.manifestFiles.firstOrNull() ?: return
        val document = context.mainProject.mergedManifest ?: return

        val services = document.getElementsByTagName("service")
        val servicesWithEditor = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until services.length) {
            val service = services.item(i) as org.w3c.dom.Element
            if (hasWatchFaceEditorMetadata(service)) {
                servicesWithEditor.add(service)
            }
        }

        val activities = document.getElementsByTagName("activity")
        val matchingActivities = mutableListOf<ActivityConfigInfo>()
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as org.w3c.dom.Element
            val info = checkActivityIntentFilters(activity)
            if (info.hasAction) {
                matchingActivities.add(info)
            }
        }

        val minSdkVersion = context.project.minSdkVersion.featureLevel

        // 1. Check duplicate watch face configuration activities
        if (matchingActivities.size > 1) {
            for (activityInfo in matchingActivities) {
                val location = context.client.xmlParser.getLocation(manifestFile, activityInfo.element)
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activities found"
                )
            }
        }

        // 2. Check "If and only if" condition
        if (servicesWithEditor.isNotEmpty() && matchingActivities.isEmpty()) {
            for (service in servicesWithEditor) {
                val location = context.client.xmlParser.getLocation(manifestFile, service)
                context.report(
                    ISSUE,
                    location,
                    "Watch face service defines WATCH_FACE_EDITOR configuration but no matching activity was found"
                )
            }
        } else if (servicesWithEditor.isEmpty() && matchingActivities.isNotEmpty()) {
            for (activityInfo in matchingActivities) {
                val location = context.client.xmlParser.getLocation(manifestFile, activityInfo.element)
                context.report(
                    ISSUE,
                    location,
                    "Watch face configuration activity found but no watch face service defines WATCH_FACE_EDITOR metadata"
                )
            }
        }

        // 3. Check minSdkVersion < 30 category requirement
        if (minSdkVersion < 30) {
            for (activityInfo in matchingActivities) {
                if (!activityInfo.hasCategory) {
                    val location = context.client.xmlParser.getLocation(manifestFile, activityInfo.element)
                    context.report(
                        ISSUE,
                        location,
                        "Watch face configuration activity is missing required category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` for minSdkVersion < 30"
                    )
                }
            }
        }
    }

    private fun hasWatchFaceEditorMetadata(service: org.w3c.dom.Element): Boolean {
        val metaDatas = getChildrenByTagName(service, "meta-data")
        for (metaData in metaDatas) {
            val name = metaData.getAttributeNS(ANDROID_URI, "name")
            if (name == METADATA_NAME) {
                val value = metaData.getAttributeNS(ANDROID_URI, "value")
                if (isWatchFaceEditor(value)) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkActivityIntentFilters(activity: org.w3c.dom.Element): ActivityConfigInfo {
        val intentFilters = getChildrenByTagName(activity, "intent-filter")
        var hasAction = false
        var hasCategory = false
        for (filter in intentFilters) {
            val actions = getChildrenByTagName(filter, "action")
            var filterHasAction = false
            for (action in actions) {
                val name = action.getAttributeNS(ANDROID_URI, "name")
                if (isWatchFaceEditor(name)) {
                    filterHasAction = true
                    hasAction = true
                }
            }
            if (filterHasAction) {
                val categories = getChildrenByTagName(filter, "category")
                for (category in categories) {
                    val name = category.getAttributeNS(ANDROID_URI, "name")
                    if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                        hasCategory = true
                    }
                }
            }
        }
        return ActivityConfigInfo(activity, hasAction, hasCategory)
    }

    private fun getChildrenByTagName(parent: org.w3c.dom.Element, tagName: String): List<org.w3c.dom.Element> {
        val list = mutableListOf<org.w3c.dom.Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element && child.tagName == tagName) {
                list.add(child)
            }
        }
        return list
    }

    private fun isWatchFaceEditor(value: String?): Boolean {
        if (value == null) return false
        return value == "WATCH_FACE_EDITOR" || value == WATCH_FACE_EDITOR
    }

    private class ActivityConfigInfo(
        val element: org.w3c.dom.Element,
        val hasAction: Boolean,
        val hasCategory: Boolean
    )
}
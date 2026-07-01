package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.project.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val services = root.getElementsByTagName("service")
        var hasWatchFaceWithConfig = false

        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metaDataNodes = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDataNodes.length) {
                val metaData = metaDataNodes.item(j) as? org.w3c.dom.Element ?: continue
                val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                    value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                    hasWatchFaceWithConfig = true
                    break
                }
            }
            if (hasWatchFaceWithConfig) {
                break
            }
        }

        if (!hasWatchFaceWithConfig) {
            return
        }

        val activities = root.getElementsByTagName("activity")
        var hasMatchingActivity = false
        val minSdkVersion = context.project.minSdkVersion.apiLevel

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as? org.w3c.dom.Element ?: continue
                
                val actions = intentFilter.getElementsByTagName("action")
                var hasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as? org.w3c.dom.Element ?: continue
                    val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (actionName == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                        hasAction = true
                        break
                    }
                }

                if (!hasAction) {
                    continue
                }

                if (minSdkVersion < 30) {
                    val categories = intentFilter.getElementsByTagName("category")
                    var hasCategory = false
                    for (k in 0 until categories.length) {
                        val category = categories.item(k) as? org.w3c.dom.Element ?: continue
                        val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                        if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                            hasCategory = true
                            break
                        }
                    }
                    if (hasCategory) {
                        hasMatchingActivity = true
                        break
                    }
                } else {
                    hasMatchingActivity = true
                    break
                }
            }
            if (hasMatchingActivity) {
                break
            }
        }

        if (!hasMatchingActivity) {
            val manifestFile = context.project.manifestFiles.firstOrNull()
            val location = if (manifestFile != null) context.getLocation(manifestFile) else context.getLocation(context.project)
            val message = if (minSdkVersion < 30) {
                "Wear configuration action metadata requires a matching activity with the WATCH_FACE_EDITOR action and WEARABLE_CONFIGURATION category"
            } else {
                "Wear configuration action metadata requires a matching activity with the WATCH_FACE_EDITOR action"
            }
            context.report(ISSUE, location, message)
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val ATTR_VALUE = "value"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "Only when a watch face service defines `wearableConfigurationAction` metadata, " +
                    "with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, " +
                    "which has an intent filter for `WATCH_FACE_EDITOR` (with " +
                    "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).",
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
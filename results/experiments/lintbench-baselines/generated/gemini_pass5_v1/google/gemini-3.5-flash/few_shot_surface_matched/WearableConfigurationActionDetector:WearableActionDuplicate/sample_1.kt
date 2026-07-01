package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val manifestFile = context.project.manifestFiles.firstOrNull() ?: return
        val document = context.project.mergedManifest ?: return

        val services = document.getElementsByTagName("service")
        val watchFaceServices = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            if (hasWearableConfigAction(service)) {
                watchFaceServices.add(service)
            }
        }

        val minSdkVersion = context.project.minSdkVersion.featureLevel
        val activities = document.getElementsByTagName("activity")
        val matchingActivities = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            if (matchesConfigAction(activity, minSdkVersion < 30)) {
                matchingActivities.add(activity)
            }
        }

        if (watchFaceServices.isNotEmpty() && matchingActivities.isEmpty()) {
            val location = Location.create(manifestFile)
            context.report(
                ISSUE,
                location,
                "The watch face service defines `wearableConfigurationAction` but no matching configuration activity was found"
            )
        } else if (watchFaceServices.isEmpty() && matchingActivities.isNotEmpty()) {
            val location = Location.create(manifestFile)
            context.report(
                ISSUE,
                location,
                "An activity is configured as a watch face editor, but no watch face service defines the `wearableConfigurationAction` metadata"
            )
        } else if (matchingActivities.size > 1) {
            val location = Location.create(manifestFile)
            context.report(
                ISSUE,
                location,
                "Duplicate watch face configuration activities found"
            )
        }
    }

    private fun hasWearableConfigAction(service: org.w3c.dom.Element): Boolean {
        val childNodes = service.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is org.w3c.dom.Element && node.tagName == "meta-data") {
                val name = node.getAttributeNS(ANDROID_URI, "name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val value = node.getAttributeNS(ANDROID_URI, "value")
                    if (value == "com.google.android.wearable.watchface.wearableConfigurationAction.WATCH_FACE_EDITOR" ||
                        value == "WATCH_FACE_EDITOR") {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun matchesConfigAction(activity: org.w3c.dom.Element, checkCategory: Boolean): Boolean {
        val childNodes = activity.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is org.w3c.dom.Element && node.tagName == "intent-filter") {
                if (intentFilterMatches(node, checkCategory)) {
                    return true
                }
            }
        }
        return false
    }

    private fun intentFilterMatches(filter: org.w3c.dom.Element, checkCategory: Boolean): Boolean {
        var hasAction = false
        var hasCategory = false
        val childNodes = filter.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is org.w3c.dom.Element) {
                if (node.tagName == "action") {
                    val name = node.getAttributeNS(ANDROID_URI, "name")
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction.WATCH_FACE_EDITOR" ||
                        name == "WATCH_FACE_EDITOR") {
                        hasAction = true
                    }
                } else if (node.tagName == "category") {
                    val name = node.getAttributeNS(ANDROID_URI, "name")
                    if (name == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                        hasCategory = true
                    }
                }
            }
        }
        return if (checkCategory) {
            hasAction && hasCategory
        } else {
            hasAction
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

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
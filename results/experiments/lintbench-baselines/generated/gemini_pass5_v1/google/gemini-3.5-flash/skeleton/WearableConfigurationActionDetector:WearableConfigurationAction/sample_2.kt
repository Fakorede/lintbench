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

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines wearableConfigurationAction metadata, there should be an activity in the same package which has an intent filter matching that action, including the WEARABLE_CONFIGURATION category if minSdkVersion is less than 30.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.client.getMergedManifest(context.project) ?: return
        val root = mergedManifest.documentElement ?: return

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        val services = getElementsByTagName(root, "service")
        for (service in services) {
            val metaDatas = getElementsByTagName(service, "meta-data")
            for (metaData in metaDatas) {
                val name = metaData.getAttributeNS(ANDROID_URI, "name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" ||
                    name == "wearableConfigurationAction"
                ) {
                    val actionValue = metaData.getAttributeNS(ANDROID_URI, "value")
                    if (actionValue.isNotEmpty()) {
                        val hasMatchingActivity = checkHasMatchingActivity(root, actionValue, minSdkVersion)
                        if (!hasMatchingActivity) {
                            val location = context.getLocation(metaData)
                            val message = if (minSdkVersion < 30) {
                                "To support configuration, there must be an activity with an intent-filter for action '$actionValue' and category 'com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION' (since minSdkVersion is less than 30)"
                            } else {
                                "To support configuration, there must be an activity with an intent-filter for action '$actionValue'"
                            }
                            context.report(
                                ISSUE,
                                metaData,
                                location,
                                message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkHasMatchingActivity(root: org.w3c.dom.Element, actionValue: String, minSdkVersion: Int): Boolean {
        val activities = getElementsByTagName(root, "activity")
        for (activity in activities) {
            val intentFilters = getElementsByTagName(activity, "intent-filter")
            for (filter in intentFilters) {
                val actions = getElementsByTagName(filter, "action")
                val categories = getElementsByTagName(filter, "category")

                val hasAction = actions.any { it.getAttributeNS(ANDROID_URI, "name") == actionValue }
                val hasCategory = if (minSdkVersion < 30) {
                    categories.any { it.getAttributeNS(ANDROID_URI, "name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" }
                } else {
                    true
                }

                if (hasAction && hasCategory) {
                    return true
                }
            }
        }
        return false
    }

    private fun getElementsByTagName(parent: org.w3c.dom.Element, name: String): List<org.w3c.dom.Element> {
        val list = parent.getElementsByTagName(name)
        val result = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until list.length) {
            val node = list.item(i)
            if (node is org.w3c.dom.Element) {
                result.add(node)
            }
        }
        return result
    }
}
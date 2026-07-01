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
        val project = context.project
        val mergedManifest = project.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val services = root.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metaDataList = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDataList.length) {
                val metaData = metaDataList.item(j) as? org.w3c.dom.Element ?: continue
                val name = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val actionValue = metaData.getAttributeNS("http://schemas.android.com/apk/res/android", "value")
                    if (actionValue.isNullOrEmpty()) continue

                    val hasMatchingActivity = checkActivity(root, actionValue, context)
                    if (!hasMatchingActivity) {
                        val location = context.getLocation(metaData)
                        val minSdk = project.minSdkVersion.apiLevel
                        val msg = if (minSdk < 30) {
                            "Under minSdkVersion 30, there must be an activity in the same package with an intent filter for action \"$actionValue\" and category \"com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION\""
                        } else {
                            "There must be an activity in the same package with an intent filter for action \"$actionValue\""
                        }
                        context.report(ISSUE, metaData, location, msg)
                    }
                }
            }
        }
    }

    private fun checkActivity(root: org.w3c.dom.Element, actionValue: String, context: Context): Boolean {
        val activities = root.getElementsByTagName("activity")
        val minSdk = context.project.minSdkVersion.apiLevel
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as? org.w3c.dom.Element ?: continue

                var hasAction = false
                val actions = filter.getElementsByTagName("action")
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as? org.w3c.dom.Element ?: continue
                    val actName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                    if (actName == actionValue) {
                        hasAction = true
                        break
                    }
                }

                if (hasAction) {
                    if (minSdk < 30) {
                        var hasCategory = false
                        val categories = filter.getElementsByTagName("category")
                        for (k in 0 until categories.length) {
                            val cat = categories.item(k) as? org.w3c.dom.Element ?: continue
                            val catName = cat.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                            if (catName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                hasCategory = true
                                break
                            }
                        }
                        if (hasCategory) {
                            return true
                        }
                    } else {
                        return true
                    }
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
            explanation = "Only when a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).",
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
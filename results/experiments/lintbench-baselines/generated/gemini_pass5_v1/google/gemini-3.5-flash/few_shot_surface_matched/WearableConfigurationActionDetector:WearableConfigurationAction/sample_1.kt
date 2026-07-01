package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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
        val manifestFile = context.mainProject.mergedManifest ?: return
        val document = context.client.getXmlDocument(manifestFile, context.client.getContents(manifestFile)) ?: return
        val root = document.documentElement ?: return

        val serviceNodes = root.getElementsByTagName("service")
        for (i in 0 until serviceNodes.length) {
            val service = serviceNodes.item(i) as? Element ?: continue
            val metaDataNodes = service.getElementsByTagName("meta-data")
            var hasWatchFaceEditor = false
            var metaDataElement: Element? = null

            for (j in 0 until metaDataNodes.length) {
                val metaData = metaDataNodes.item(j) as? Element ?: continue
                val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = metaData.getAttributeNS(ANDROID_URI, "value")
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
                    value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                    hasWatchFaceEditor = true
                    metaDataElement = metaData
                    break
                }
            }

            if (hasWatchFaceEditor) {
                var foundMatchingActivity = false
                val activityNodes = root.getElementsByTagName("activity")
                val minSdk = context.project.minSdkVersion.apiLevel

                for (k in 0 until activityNodes.length) {
                    val activity = activityNodes.item(k) as? Element ?: continue
                    val intentFilters = activity.getElementsByTagName("intent-filter")
                    for (l in 0 until intentFilters.length) {
                        val filter = intentFilters.item(l) as? Element ?: continue
                        var hasAction = false
                        var hasCategory = false

                        val actions = filter.getElementsByTagName("action")
                        for (m in 0 until actions.length) {
                            val action = actions.item(m) as? Element ?: continue
                            if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
                                hasAction = true
                                break
                            }
                        }

                        if (minSdk < 30) {
                            val categories = filter.getElementsByTagName("category")
                            for (m in 0 until categories.length) {
                                val category = categories.item(m) as? Element ?: continue
                                if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                    hasCategory = true
                                    break
                                }
                            }
                        } else {
                            hasCategory = true
                        }

                        if (hasAction && hasCategory) {
                            foundMatchingActivity = true
                            break
                        }
                    }
                    if (foundMatchingActivity) break
                }

                if (!foundMatchingActivity) {
                    val location = context.client.getXmlParser().getLocation(manifestFile, metaDataElement ?: service)
                    val message = if (minSdk < 30) {
                        "Wearable configuration action 'WATCH_FACE_EDITOR' requires a matching activity with an intent-filter for 'com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR' and category 'com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION'."
                    } else {
                        "Wearable configuration action 'WATCH_FACE_EDITOR' requires a matching activity with an intent-filter for 'com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR'."
                    }
                    context.report(ISSUE, location, message)
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wearable configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, \
                which has an intent filter for `WATCH_FACE_EDITOR` (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if `minSdkVersion` is less than 30).
            """.trimIndent(),
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
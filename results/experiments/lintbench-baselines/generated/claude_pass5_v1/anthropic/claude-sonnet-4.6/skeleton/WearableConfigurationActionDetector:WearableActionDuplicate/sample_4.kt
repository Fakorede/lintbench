package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same \
                package that has an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` is \
                less than 30, the intent filter should also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val minSdk = mainProject.minSdk

        val applicationElement = root.childElements().firstOrNull { it.tagName == TAG_APPLICATION }
            ?: return

        // Find all watch face services that declare wearableConfigurationAction = WATCH_FACE_EDITOR
        val watchFaceServicesWithConfig = mutableListOf<Element>()
        for (service in applicationElement.childElements().filter { it.tagName == TAG_SERVICE }) {
            for (meta in service.childElements().filter { it.tagName == TAG_META_DATA }) {
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    watchFaceServicesWithConfig.add(service)
                    break
                }
            }
        }

        if (watchFaceServicesWithConfig.isEmpty()) return

        // Find all activities with intent filter for WATCH_FACE_EDITOR
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()
        for (activity in applicationElement.childElements().filter { it.tagName == TAG_ACTIVITY }) {
            for (intentFilter in activity.childElements().filter { it.tagName == TAG_INTENT_FILTER }) {
                val hasWatchFaceEditorAction = intentFilter.childElements()
                    .filter { it.tagName == TAG_ACTION }
                    .any { it.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION }

                if (hasWatchFaceEditorAction) {
                    if (minSdk >= 30) {
                        activitiesWithWatchFaceEditor.add(activity)
                    } else {
                        // Also require the WEARABLE_CONFIGURATION category for minSdk < 30
                        val hasWearableConfigCategory = intentFilter.childElements()
                            .filter { it.tagName == TAG_CATEGORY }
                            .any {
                                it.getAttributeNS(ANDROID_URI, ATTR_NAME) ==
                                    WEARABLE_CONFIGURATION_CATEGORY
                            }
                        if (hasWearableConfigCategory) {
                            activitiesWithWatchFaceEditor.add(activity)
                        }
                    }
                    break
                }
            }
        }

        // If there are multiple watch face services with config, or multiple activities
        // with the watch face editor intent filter, report the issue
        if (watchFaceServicesWithConfig.size > 1 || activitiesWithWatchFaceEditor.size > 1) {
            // Report on each duplicate beyond the first
            if (watchFaceServicesWithConfig.size > 1) {
                for (i in 1 until watchFaceServicesWithConfig.size) {
                    val location = context.getLocation(watchFaceServicesWithConfig[i])
                    context.report(
                        ISSUE,
                        location,
                        "Duplicate watch face configuration activities found: multiple watch " +
                            "face services declare `wearableConfigurationAction`"
                    )
                }
            }
            if (activitiesWithWatchFaceEditor.size > 1) {
                for (i in 1 until activitiesWithWatchFaceEditor.size) {
                    val location = context.getLocation(activitiesWithWatchFaceEditor[i])
                    context.report(
                        ISSUE,
                        location,
                        "Duplicate watch face configuration activities found: multiple " +
                            "activities declare intent filter for `WATCH_FACE_EDITOR`"
                    )
                }
            }
        }
    }

    private fun Element.childElements(): Sequence<Element> = sequence {
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                yield(child as Element)
            }
        }
    }
}
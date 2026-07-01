package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_SDK
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

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
                package which has an intent filter for `WATCH_FACE_EDITOR`. If the minSdkVersion \
                is less than 30, the activity should also have a \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category \
                in the intent filter.
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
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        // Determine minSdkVersion
        val minSdkVersion = getMinSdkVersion(root)

        // Find the application element
        val applicationElement = root.childElements().firstOrNull { it.tagName == TAG_APPLICATION }
            ?: return

        // Collect all services that have the wearableConfigurationAction metadata = WATCH_FACE_EDITOR
        val servicesWithMetadata = mutableListOf<Element>()
        for (service in applicationElement.childElements().filter { it.tagName == TAG_SERVICE }) {
            for (meta in service.childElements().filter { it.tagName == TAG_META_DATA }) {
                val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA && value == WATCH_FACE_EDITOR_ACTION) {
                    servicesWithMetadata.add(service)
                    break
                }
            }
        }

        if (servicesWithMetadata.isEmpty()) {
            // No watch face service with wearableConfigurationAction metadata, nothing to check
            return
        }

        // Collect all activities that have an intent filter for WATCH_FACE_EDITOR
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()
        for (activity in applicationElement.childElements().filter { it.tagName == TAG_ACTIVITY }) {
            if (activityHasWatchFaceEditorIntentFilter(activity, minSdkVersion)) {
                activitiesWithWatchFaceEditor.add(activity)
            }
        }

        // If there are multiple activities with WATCH_FACE_EDITOR intent filter, report on extras
        if (activitiesWithWatchFaceEditor.size > 1) {
            // Report on all but the first (or report on all duplicates)
            for (i in 1 until activitiesWithWatchFaceEditor.size) {
                val activity = activitiesWithWatchFaceEditor[i]
                val location = context.getLocation(activity)
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activities found: there should be " +
                        "exactly one activity with intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        }
    }

    private fun activityHasWatchFaceEditorIntentFilter(
        activity: Element,
        minSdkVersion: Int
    ): Boolean {
        for (intentFilter in activity.childElements().filter { it.tagName == TAG_INTENT_FILTER }) {
            val hasWatchFaceEditorAction = intentFilter.childElements()
                .filter { it.tagName == "action" }
                .any { it.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION }

            if (!hasWatchFaceEditorAction) continue

            if (minSdkVersion < 30) {
                // Also need the WEARABLE_CONFIGURATION category
                val hasWearableConfigCategory = intentFilter.childElements()
                    .filter { it.tagName == "category" }
                    .any {
                        it.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
                    }
                if (hasWearableConfigCategory) {
                    return true
                }
            } else {
                return true
            }
        }
        return false
    }

    private fun getMinSdkVersion(root: Element): Int {
        val usesSdk = root.childElements().firstOrNull { it.tagName == TAG_USES_SDK }
        if (usesSdk != null) {
            val minSdk = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
            if (minSdk.isNotEmpty()) {
                return minSdk.toIntOrNull() ?: 1
            }
        }
        return 1
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                result.add(child)
            }
        }
        return result
    }
}
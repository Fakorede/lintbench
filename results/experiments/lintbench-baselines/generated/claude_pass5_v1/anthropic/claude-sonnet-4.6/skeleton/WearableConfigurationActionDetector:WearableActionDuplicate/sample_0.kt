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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.xml.AndroidManifest.NODE_ACTION
import com.android.xml.AndroidManifest.NODE_CATEGORY
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
                package which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if minSdkVersion is less than 30).
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
        private const val WATCH_FACE_SERVICE =
            "com.google.android.wearable.watchface.WatchFaceService"
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = if (context.project.isLibrary) {
            context.mainProject
        } else {
            context.project
        }

        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val minSdkVersion = mainProject.minSdkVersion.apiLevel

        // Find the application element
        val applicationElement = root.childElements().firstOrNull {
            it.tagName == TAG_APPLICATION
        } ?: return

        val children = applicationElement.childElements()

        // Collect all services that have wearableConfigurationAction = WATCH_FACE_EDITOR metadata
        val watchFaceServices = children.filter { element ->
            element.tagName == TAG_SERVICE && hasWearableConfigurationActionMetadata(element)
        }

        if (watchFaceServices.isEmpty()) {
            return
        }

        // Collect all activities that have an intent filter for WATCH_FACE_EDITOR
        val configurationActivities = children.filter { element ->
            element.tagName == TAG_ACTIVITY && hasWatchFaceEditorIntentFilter(element, minSdkVersion)
        }

        val configActivityCount = configurationActivities.size

        if (configActivityCount > 1) {
            // Report duplicate configuration activities
            for (activity in configurationActivities) {
                val location = context.getLocation(activity)
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activities found: there should be " +
                        "exactly one activity with an intent filter for `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        } else if (configActivityCount == 0) {
            // Report that there's a watch face service with wearableConfigurationAction
            // but no matching activity
            for (service in watchFaceServices) {
                val location = context.getLocation(service)
                context.report(
                    ISSUE,
                    location,
                    "Watch face service has `wearableConfigurationAction` metadata with value " +
                        "`$WATCH_FACE_EDITOR_ACTION` but no matching activity with an intent " +
                        "filter for `$WATCH_FACE_EDITOR_ACTION` was found"
                )
            }
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        return serviceElement.childElements().any { child ->
            child.tagName == TAG_META_DATA &&
                child.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                child.getAttributeNS(ANDROID_URI, ATTR_VALUE) == WATCH_FACE_EDITOR_ACTION
        }
    }

    private fun hasWatchFaceEditorIntentFilter(
        activityElement: Element,
        minSdkVersion: Int
    ): Boolean {
        val intentFilters = activityElement.childElements().filter {
            it.tagName == TAG_INTENT_FILTER
        }

        for (intentFilter in intentFilters) {
            val filterChildren = intentFilter.childElements()

            val hasWatchFaceEditorAction = filterChildren.any { child ->
                child.tagName == NODE_ACTION &&
                    child.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION
            }

            if (!hasWatchFaceEditorAction) continue

            if (minSdkVersion < 30) {
                val hasWearableConfigurationCategory = filterChildren.any { child ->
                    child.tagName == NODE_CATEGORY &&
                        child.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
                }
                if (hasWearableConfigurationCategory) {
                    return true
                }
            } else {
                return true
            }
        }

        return false
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                result.add(node)
            }
        }
        return result
    }
}
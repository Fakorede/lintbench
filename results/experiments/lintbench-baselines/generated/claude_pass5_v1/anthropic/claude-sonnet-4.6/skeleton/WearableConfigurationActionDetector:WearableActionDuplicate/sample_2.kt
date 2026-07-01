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
                package which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                minSdkVersion is less than 30).
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
            "android.service.wallpaper.WallpaperService"

        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        // We handle everything in checkMergedProject; this method is intentionally left empty.
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        val minSdkVersion = mainProject.minSdk

        // Collect all services that have the wearableConfigurationAction metadata with WATCH_FACE_EDITOR
        val servicesWithMetadata = mutableListOf<Element>()
        // Collect all activities that have an intent-filter with WATCH_FACE_EDITOR action
        val activitiesWithWatchFaceEditor = mutableListOf<Element>()

        val applicationElement = root.childNodes.let { nodes ->
            (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>()
                .firstOrNull { it.tagName == TAG_APPLICATION }
        } ?: return

        val appChildren = applicationElement.childNodes
        for (i in 0 until appChildren.length) {
            val child = appChildren.item(i) as? Element ?: continue
            when (child.tagName) {
                TAG_SERVICE -> {
                    if (hasWearableConfigurationActionMetadata(child)) {
                        servicesWithMetadata.add(child)
                    }
                }
                TAG_ACTIVITY -> {
                    if (hasWatchFaceEditorIntentFilter(child, minSdkVersion)) {
                        activitiesWithWatchFaceEditor.add(child)
                    }
                }
            }
        }

        // If no services define the metadata, nothing to check
        if (servicesWithMetadata.isEmpty()) return

        // If there are multiple activities with WATCH_FACE_EDITOR intent filter, report duplicates
        if (activitiesWithWatchFaceEditor.size > 1) {
            // Report on all but the first activity (duplicates)
            for (idx in 1 until activitiesWithWatchFaceEditor.size) {
                val activity = activitiesWithWatchFaceEditor[idx]
                val activityName = activity.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: "unknown"
                context.report(
                    issue = ISSUE,
                    location = context.getLocation(activity),
                    message = "Duplicate watch face configuration activity `$activityName` found; " +
                        "there should be exactly one activity with intent filter for `$WATCH_FACE_EDITOR_ACTION`.",
                )
            }
        }

        // If there are no activities with the WATCH_FACE_EDITOR intent filter, report on each service
        if (activitiesWithWatchFaceEditor.isEmpty()) {
            for (service in servicesWithMetadata) {
                val serviceName = service.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: "unknown"
                val message = if (minSdkVersion < 30) {
                    "Watch face service `$serviceName` defines `wearableConfigurationAction` metadata " +
                        "with value `WATCH_FACE_EDITOR`, but no activity with intent filter for " +
                        "`$WATCH_FACE_EDITOR_ACTION` and category `$WEARABLE_CONFIGURATION_CATEGORY` was found."
                } else {
                    "Watch face service `$serviceName` defines `wearableConfigurationAction` metadata " +
                        "with value `WATCH_FACE_EDITOR`, but no activity with intent filter for " +
                        "`$WATCH_FACE_EDITOR_ACTION` was found."
                }
                context.report(
                    issue = ISSUE,
                    location = context.getLocation(service),
                    message = message,
                )
            }
        }
    }

    private fun hasWearableConfigurationActionMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasWatchFaceEditorIntentFilter(activityElement: Element, minSdkVersion: Int): Boolean {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                if (intentFilterHasWatchFaceEditor(child, minSdkVersion)) {
                    return true
                }
            }
        }
        return false
    }

    private fun intentFilterHasWatchFaceEditor(intentFilter: Element, minSdkVersion: Int): Boolean {
        var hasAction = false
        var hasCategory = false

        val children = intentFilter.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            when (child.tagName) {
                TAG_ACTION -> {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WATCH_FACE_EDITOR_ACTION) {
                        hasAction = true
                    }
                }
                TAG_CATEGORY -> {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                        hasCategory = true
                    }
                }
            }
        }

        return if (minSdkVersion < 30) {
            hasAction && hasCategory
        } else {
            hasAction
        }
    }

    // Helper to get location from an Element using context
    private fun Context.getLocation(element: Element): Location {
        return Location.create(file)
    }
}
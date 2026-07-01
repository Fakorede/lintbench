package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
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
import com.android.xml.AndroidManifest.NODE_APPLICATION
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                `minSdkVersion` is less than 30).
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
        private const val WATCH_FACE_EDITOR_VALUE = WATCH_FACE_EDITOR_ACTION

        private fun getAttributeValue(element: Element, namespace: String, name: String): String? {
            val attr = element.getAttributeNodeNS(namespace, name) ?: return null
            return attr.value
        }

        private fun getChildren(element: Element, tagName: String): List<Element> {
            val result = mutableListOf<Element>()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.tagName == tagName) {
                    result.add(child)
                }
            }
            return result
        }

        private fun getAllChildren(element: Element): List<Element> {
            val result = mutableListOf<Element>()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    result.add(child)
                }
            }
            return result
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(NODE_APPLICATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        checkApplication(context, element)
    }

    private fun checkApplication(context: XmlContext, application: Element) {
        val minSdkVersion = context.project.minSdk

        // Find all services that declare wearableConfigurationAction metadata with WATCH_FACE_EDITOR value
        val services = getChildren(application, TAG_SERVICE)
        val servicesWithWatchFaceEditorMetadata = mutableListOf<Element>()

        for (service in services) {
            val metaDatas = getChildren(service, TAG_META_DATA)
            for (metaData in metaDatas) {
                val metaName = getAttributeValue(metaData, ANDROID_URI, ATTR_NAME)
                val metaValue = getAttributeValue(metaData, ANDROID_URI, ATTR_VALUE)
                if (metaName == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    metaValue == WATCH_FACE_EDITOR_VALUE
                ) {
                    servicesWithWatchFaceEditorMetadata.add(service)
                    break
                }
            }
        }

        if (servicesWithWatchFaceEditorMetadata.isEmpty()) {
            return
        }

        // Find all activities that have an intent filter with WATCH_FACE_EDITOR action
        val activities = getChildren(application, TAG_ACTIVITY)
        val activitiesWithWatchFaceEditorFilter = mutableListOf<Element>()

        for (activity in activities) {
            val intentFilters = getChildren(activity, TAG_INTENT_FILTER)
            for (intentFilter in intentFilters) {
                val hasWatchFaceEditorAction = getChildren(intentFilter, TAG_ACTION).any { action ->
                    getAttributeValue(action, ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION
                }
                if (hasWatchFaceEditorAction) {
                    activitiesWithWatchFaceEditorFilter.add(activity)
                    break
                }
            }
        }

        // For each service with the metadata, check that a matching activity exists
        for (service in servicesWithWatchFaceEditorMetadata) {
            if (activitiesWithWatchFaceEditorFilter.isEmpty()) {
                // No activity with WATCH_FACE_EDITOR intent filter found
                val metaDataElement = getChildren(service, TAG_META_DATA).firstOrNull { metaData ->
                    val metaName = getAttributeValue(metaData, ANDROID_URI, ATTR_NAME)
                    val metaValue = getAttributeValue(metaData, ANDROID_URI, ATTR_VALUE)
                    metaName == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                            metaValue == WATCH_FACE_EDITOR_VALUE
                }
                val reportElement = metaDataElement ?: service
                context.report(
                    ISSUE,
                    reportElement,
                    context.getLocation(reportElement),
                    "No activity found with an intent filter for action `$WATCH_FACE_EDITOR_ACTION`"
                )
            } else if (minSdkVersion < 30) {
                // Check that at least one activity also has the WEARABLE_CONFIGURATION category
                val hasActivityWithCategory = activitiesWithWatchFaceEditorFilter.any { activity ->
                    val intentFilters = getChildren(activity, TAG_INTENT_FILTER)
                    intentFilters.any { intentFilter ->
                        val hasAction = getChildren(intentFilter, TAG_ACTION).any { action ->
                            getAttributeValue(action, ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION
                        }
                        val hasCategory = getChildren(intentFilter, TAG_CATEGORY).any { category ->
                            getAttributeValue(category, ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY
                        }
                        hasAction && hasCategory
                    }
                }

                if (!hasActivityWithCategory) {
                    val metaDataElement = getChildren(service, TAG_META_DATA).firstOrNull { metaData ->
                        val metaName = getAttributeValue(metaData, ANDROID_URI, ATTR_NAME)
                        val metaValue = getAttributeValue(metaData, ANDROID_URI, ATTR_VALUE)
                        metaName == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                                metaValue == WATCH_FACE_EDITOR_VALUE
                    }
                    val reportElement = metaDataElement ?: service
                    context.report(
                        ISSUE,
                        reportElement,
                        context.getLocation(reportElement),
                        "Activity with intent filter for `$WATCH_FACE_EDITOR_ACTION` should also " +
                                "have category `$WEARABLE_CONFIGURATION_CATEGORY` when minSdkVersion < 30"
                    )
                }
            }
        }

        // Also check: if an activity declares WATCH_FACE_EDITOR action but no service has the metadata
        // (the reverse check - not required by the spec but let's check for activities without matching service)
        // Actually per spec: only when a service defines the metadata should there be an activity.
        // We should also warn if an activity has the filter but no service has the metadata.
        // But the spec says "Only when a watch face service defines wearableConfigurationAction metadata...
        // there should be an activity" - this implies the service is the source of truth.
        // Let's also check if there are activities with WATCH_FACE_EDITOR but no service metadata.
        if (servicesWithWatchFaceEditorMetadata.isEmpty() && activitiesWithWatchFaceEditorFilter.isNotEmpty()) {
            for (activity in activitiesWithWatchFaceEditorFilter) {
                context.report(
                    ISSUE,
                    activity,
                    context.getLocation(activity),
                    "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION` but no watch face service " +
                            "declares `$WEARABLE_CONFIGURATION_ACTION_METADATA` metadata"
                )
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        // The main logic is handled in visitElement for the manifest
        // This is called after all manifests are merged
    }
}
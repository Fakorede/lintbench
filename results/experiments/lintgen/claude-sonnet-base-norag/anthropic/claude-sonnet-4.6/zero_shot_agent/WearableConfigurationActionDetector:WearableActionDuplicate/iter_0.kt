package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import org.w3c.dom.Element
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val TAG_META_DATA = "meta-data"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If a watch face service defines `wearableConfigurationAction` metadata with the \
                value `WATCH_FACE_EDITOR`, there should be an activity in the same package which \
                has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                minSdkVersion is less than 30).

                See https://developer.android.com/training/wearables/watch-faces/configuration \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )
    }

    // Data class to hold information about a service with wearableConfigurationAction metadata
    private data class ServiceInfo(
        val element: Element,
        val context: XmlContext
    )

    // Data class to hold information about an activity with WATCH_FACE_EDITOR intent filter
    private data class ActivityInfo(
        val element: Element,
        val hasWearableConfigurationCategory: Boolean
    )

    // Per-manifest state
    private val servicesWithMetadata = mutableListOf<ServiceInfo>()
    private val activitiesWithWatchFaceEditor = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> checkService(context, element)
            TAG_ACTIVITY -> checkActivity(context, element)
        }
    }

    private fun checkService(context: XmlContext, element: Element) {
        val metaDataChildren = element.childNodes
        for (i in 0 until metaDataChildren.length) {
            val child = metaDataChildren.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    servicesWithMetadata.add(ServiceInfo(element, context))
                    return
                }
            }
        }
    }

    private fun checkActivity(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_INTENT_FILTER) {
                val intentFilterChildren = child.childNodes
                var hasWatchFaceEditorAction = false
                var hasWearableConfigurationCategory = false

                for (j in 0 until intentFilterChildren.length) {
                    val filterChild = intentFilterChildren.item(j)
                    if (filterChild is Element) {
                        when (filterChild.tagName) {
                            TAG_ACTION -> {
                                val actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                    hasWatchFaceEditorAction = true
                                }
                            }
                            TAG_CATEGORY -> {
                                val categoryName =
                                    filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                    hasWearableConfigurationCategory = true
                                }
                            }
                        }
                    }
                }

                if (hasWatchFaceEditorAction) {
                    activitiesWithWatchFaceEditor.add(
                        ActivityInfo(element, hasWearableConfigurationCategory)
                    )
                    return
                }
            }
        }
    }

    override fun beforeCheckFile(context: Context) {
        servicesWithMetadata.clear()
        activitiesWithWatchFaceEditor.clear()
    }

    override fun afterCheckFile(context: Context) {
        if (context !is XmlContext) return

        val minSdkVersion = context.project.minSdkVersion.apiLevel

        // For each service with wearableConfigurationAction metadata, check if there's
        // a corresponding activity with the WATCH_FACE_EDITOR intent filter
        for (serviceInfo in servicesWithMetadata) {
            val matchingActivity = findMatchingActivity(minSdkVersion)

            if (matchingActivity == null) {
                // No matching activity found - report on the service
                val message = if (minSdkVersion < 30) {
                    "Watch face service has `wearableConfigurationAction` metadata with value " +
                        "`WATCH_FACE_EDITOR`, but no activity in the same package has an intent " +
                        "filter for `$WATCH_FACE_EDITOR_ACTION` with category " +
                        "`$WEARABLE_CONFIGURATION_CATEGORY`"
                } else {
                    "Watch face service has `wearableConfigurationAction` metadata with value " +
                        "`WATCH_FACE_EDITOR`, but no activity in the same package has an intent " +
                        "filter for `$WATCH_FACE_EDITOR_ACTION`"
                }
                serviceInfo.context.report(
                    ISSUE,
                    serviceInfo.element,
                    serviceInfo.context.getNameLocation(serviceInfo.element),
                    message
                )
            }
        }

        // Check for activities with WATCH_FACE_EDITOR but no corresponding service with metadata
        if (activitiesWithWatchFaceEditor.isNotEmpty() && servicesWithMetadata.isEmpty()) {
            for (activityInfo in activitiesWithWatchFaceEditor) {
                context.report(
                    ISSUE,
                    activityInfo.element,
                    context.getNameLocation(activityInfo.element),
                    "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION`, but no watch " +
                        "face service in the same package defines `wearableConfigurationAction` " +
                        "metadata with value `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        }

        // Check for activities missing the WEARABLE_CONFIGURATION category when minSdk < 30
        if (minSdkVersion < 30 && servicesWithMetadata.isNotEmpty()) {
            for (activityInfo in activitiesWithWatchFaceEditor) {
                if (!activityInfo.hasWearableConfigurationCategory) {
                    context.report(
                        ISSUE,
                        activityInfo.element,
                        context.getNameLocation(activityInfo.element),
                        "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION`, but is " +
                            "missing the `$WEARABLE_CONFIGURATION_CATEGORY` category. This is " +
                            "required when minSdkVersion is less than 30."
                    )
                }
            }
        }
    }

    private fun findMatchingActivity(minSdkVersion: Int): ActivityInfo? {
        return activitiesWithWatchFaceEditor.firstOrNull { activityInfo ->
            if (minSdkVersion < 30) {
                activityInfo.hasWearableConfigurationCategory
            } else {
                true
            }
        }
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false
}
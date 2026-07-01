package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
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
        private const val TAG_INTENT_FILTER = "intent-filter"

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

    private data class ServiceInfo(
        val element: Element,
        val location: Location,
        val xmlContext: XmlContext
    )

    private data class ActivityInfo(
        val element: Element,
        val location: Location,
        val hasWearableConfigurationCategory: Boolean,
        val xmlContext: XmlContext
    )

    // Per-file state
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
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    servicesWithMetadata.add(
                        ServiceInfo(element, context.getNameLocation(element), context)
                    )
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
                var hasWatchFaceEditorAction = false
                var hasWearableConfigurationCategory = false

                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
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
                        ActivityInfo(
                            element,
                            context.getNameLocation(element),
                            hasWearableConfigurationCategory,
                            context
                        )
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

        if (servicesWithMetadata.isNotEmpty() && activitiesWithWatchFaceEditor.isEmpty()) {
            // Service has metadata but no matching activity with WATCH_FACE_EDITOR action
            for (serviceInfo in servicesWithMetadata) {
                serviceInfo.xmlContext.report(
                    ISSUE,
                    serviceInfo.element,
                    serviceInfo.location,
                    "Watch face service has `wearableConfigurationAction` metadata " +
                        "with value `$WATCH_FACE_EDITOR_ACTION`, but no activity in " +
                        "the same package has an intent filter for " +
                        "`$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        } else if (servicesWithMetadata.isNotEmpty() && activitiesWithWatchFaceEditor.isNotEmpty()) {
            // Both exist - check if minSdkVersion < 30 requires the category
            if (minSdkVersion < 30) {
                val hasValidActivity =
                    activitiesWithWatchFaceEditor.any { it.hasWearableConfigurationCategory }
                if (!hasValidActivity) {
                    for (activityInfo in activitiesWithWatchFaceEditor) {
                        activityInfo.xmlContext.report(
                            ISSUE,
                            activityInfo.element,
                            activityInfo.location,
                            "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION`" +
                                ", but is missing the `$WEARABLE_CONFIGURATION_CATEGORY`" +
                                " category. This is required when minSdkVersion is " +
                                "less than 30."
                        )
                    }
                }
            }
        } else if (servicesWithMetadata.isEmpty() && activitiesWithWatchFaceEditor.isNotEmpty()) {
            // Activity has WATCH_FACE_EDITOR intent filter but no service with metadata
            for (activityInfo in activitiesWithWatchFaceEditor) {
                activityInfo.xmlContext.report(
                    ISSUE,
                    activityInfo.element,
                    activityInfo.location,
                    "Activity has intent filter for `$WATCH_FACE_EDITOR_ACTION`, but no watch " +
                        "face service in the same package defines " +
                        "`$WEARABLE_CONFIGURATION_ACTION_METADATA` metadata with value " +
                        "`$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        }
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean = false
}
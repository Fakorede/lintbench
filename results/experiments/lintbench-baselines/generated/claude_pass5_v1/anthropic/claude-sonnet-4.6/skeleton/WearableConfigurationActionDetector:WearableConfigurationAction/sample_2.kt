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

        private const val META_DATA_WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
    }

    // Map from XmlContext to parsed manifest data, collected during visitDocument
    private val manifestData = mutableListOf<ManifestData>()

    private data class ServiceConfigData(
        val location: Location,
        val metaDataValue: String,
    )

    private data class ActivityIntentData(
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigurationCategory: Boolean,
    )

    private data class ManifestData(
        val context: XmlContext,
        val serviceConfigs: List<ServiceConfigData>,
        val activityIntents: List<ActivityIntentData>,
        val minSdkVersion: Int,
    )

    override fun getApplicableElements(): Collection<String> = listOf(TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_APPLICATION) return

        val serviceConfigs = mutableListOf<ServiceConfigData>()
        val activityIntents = mutableListOf<ActivityIntentData>()

        // Iterate children of <application>
        val appChildren = element.childNodes
        for (i in 0 until appChildren.length) {
            val appChild = appChildren.item(i) as? Element ?: continue

            when (appChild.tagName) {
                TAG_SERVICE -> {
                    // Look for meta-data children with wearableConfigurationAction
                    val serviceChildren = appChild.childNodes
                    for (j in 0 until serviceChildren.length) {
                        val serviceChild = serviceChildren.item(j) as? Element ?: continue
                        if (serviceChild.tagName == TAG_META_DATA) {
                            val name = serviceChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            val value = serviceChild.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                            if (name == META_DATA_WEARABLE_CONFIGURATION_ACTION &&
                                value == WATCH_FACE_EDITOR_ACTION
                            ) {
                                serviceConfigs.add(
                                    ServiceConfigData(
                                        location = context.getLocation(serviceChild),
                                        metaDataValue = value,
                                    )
                                )
                            }
                        }
                    }
                }

                TAG_ACTIVITY -> {
                    // Look for intent-filter children
                    val activityChildren = appChild.childNodes
                    for (j in 0 until activityChildren.length) {
                        val activityChild = activityChildren.item(j) as? Element ?: continue
                        if (activityChild.tagName == TAG_INTENT_FILTER) {
                            var hasWatchFaceEditorAction = false
                            var hasWearableConfigurationCategory = false

                            val filterChildren = activityChild.childNodes
                            for (k in 0 until filterChildren.length) {
                                val filterChild = filterChildren.item(k) as? Element ?: continue
                                when (filterChild.tagName) {
                                    TAG_ACTION -> {
                                        val actionName =
                                            filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
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

                            if (hasWatchFaceEditorAction) {
                                activityIntents.add(
                                    ActivityIntentData(
                                        hasWatchFaceEditorAction = true,
                                        hasWearableConfigurationCategory =
                                            hasWearableConfigurationCategory,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (serviceConfigs.isNotEmpty() || activityIntents.isNotEmpty()) {
            val minSdkVersion = context.project.minSdk
            manifestData.add(
                ManifestData(
                    context = context,
                    serviceConfigs = serviceConfigs,
                    activityIntents = activityIntents,
                    minSdkVersion = minSdkVersion,
                )
            )
        }
    }

    override fun checkMergedProject(context: Context) {
        for (data in manifestData) {
            val xmlContext = data.context
            val minSdk = data.minSdkVersion
            val requiresCategory = minSdk < 30

            for (serviceConfig in data.serviceConfigs) {
                // Check if there is a matching activity
                val matchingActivity = data.activityIntents.find { activityIntent ->
                    activityIntent.hasWatchFaceEditorAction &&
                        (!requiresCategory || activityIntent.hasWearableConfigurationCategory)
                }

                if (matchingActivity == null) {
                    if (requiresCategory) {
                        xmlContext.report(
                            ISSUE,
                            serviceConfig.location,
                            "No activity in this package has an intent filter for action " +
                                "`$WATCH_FACE_EDITOR_ACTION` with category " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` (required when " +
                                "minSdkVersion < 30), but the watch face service defines " +
                                "`$META_DATA_WEARABLE_CONFIGURATION_ACTION` metadata",
                        )
                    } else {
                        xmlContext.report(
                            ISSUE,
                            serviceConfig.location,
                            "No activity in this package has an intent filter for action " +
                                "`$WATCH_FACE_EDITOR_ACTION`, but the watch face service " +
                                "defines `$META_DATA_WEARABLE_CONFIGURATION_ACTION` metadata",
                        )
                    }
                }
            }

            // Also check: if there's an activity with WATCH_FACE_EDITOR but no service
            // defines the metadata, that's potentially an issue too (but not required by spec)
            // Per specification, we only warn when service defines metadata but no matching
            // activity exists. So we skip the reverse check here.
        }
    }
}
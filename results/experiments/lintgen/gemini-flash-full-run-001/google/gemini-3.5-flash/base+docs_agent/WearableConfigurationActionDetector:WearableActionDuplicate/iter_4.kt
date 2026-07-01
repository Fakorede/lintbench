package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                Each watch face service that supports configuration should have at most one \
                corresponding configuration activity in the same package. Having multiple \
                activities with the same configuration action is not supported.
                
                Additionally, if a watch face service defines the `wearableConfigurationAction` \
                metadata, there must be a corresponding activity with the same intent filter action. \
                If the `minSdkVersion` is less than 30, this activity must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        
        // Find all services with the metadata
        val services = root.getElementsByTagName("service")
        val servicesWithMeta = mutableListOf<Element>()
        
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as Element
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
                    val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                    if (isWatchFaceEditor(value)) {
                        servicesWithMeta.add(service)
                        break
                    }
                }
            }
        }

        // Find all activities with the configuration action
        val activities = root.getElementsByTagName("activity")
        val configActivities = mutableListOf<Element>()
        val configActivitiesMissingCategory = mutableListOf<Element>()

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            var hasConfigAction = false
            var hasConfigCategory = false

            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as Element
                
                val actions = intentFilter.getElementsByTagName("action")
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (isWatchFaceEditor(actionName)) {
                        hasConfigAction = true
                    }
                }

                val categories = intentFilter.getElementsByTagName("category")
                for (k in 0 until categories.length) {
                    val category = categories.item(k) as Element
                    val categoryName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (categoryName == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                        hasConfigCategory = true
                    }
                }
            }

            if (hasConfigAction) {
                configActivities.add(activity)
                val minSdkVersion = context.project.minSdkVersion.apiLevel
                if (minSdkVersion < 30 && !hasConfigCategory) {
                    configActivitiesMissingCategory.add(activity)
                }
            }
        }

        // 1. Check for duplicates
        if (configActivities.size > 1) {
            for (activity in configActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Duplicate watch face configuration activities found"
                )
            }
            return
        }

        // 2. Check "if and only if" condition
        val hasService = servicesWithMeta.isNotEmpty()
        val hasActivity = configActivities.isNotEmpty()

        if (hasService && !hasActivity) {
            for (service in servicesWithMeta) {
                context.report(
                    ISSUE,
                    service,
                    context.getNameLocation(service),
                    "Watch face service defines wearableConfigurationAction but no matching configuration activity was found"
                )
            }
        } else if (!hasService && hasActivity) {
            for (activity in configActivities) {
                context.report(
                    ISSUE,
                    activity,
                    context.getNameLocation(activity),
                    "Configuration activity found but no watch face service defines wearableConfigurationAction"
                )
            }
        }

        // 3. Check for missing category if minSdkVersion < 30
        for (activity in configActivitiesMissingCategory) {
            context.report(
                ISSUE,
                activity,
                context.getNameLocation(activity),
                "Configuration activity is missing required category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` for minSdkVersion < 30"
            )
        }
    }

    private fun isWatchFaceEditor(value: String?): Boolean {
        if (value == null) return false
        return value == "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" ||
               value == "WATCH_FACE_EDITOR" ||
               value.endsWith(".WATCH_FACE_EDITOR")
    }
}
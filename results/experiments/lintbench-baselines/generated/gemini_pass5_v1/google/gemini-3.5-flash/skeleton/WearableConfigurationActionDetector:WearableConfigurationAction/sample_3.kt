package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.Location

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val servicesWithMetadata = mutableListOf<ServiceMetadataInfo>()
    private val activities = mutableListOf<ActivityInfo>()

    class ServiceMetadataInfo(
        val location: Location,
        val name: String,
        val value: String
    )

    class ActivityInfo(
        val actions: List<String>,
        val categories: List<String>
    )

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = "When a watch face service defines a wearableConfigurationAction " +
                    "metadata with the value WATCH_FACE_EDITOR, there must be an activity " +
                    "in the same package with an intent filter for WATCH_FACE_EDITOR. " +
                    "If minSdkVersion is less than 30, that intent filter must also include " +
                    "the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        val services = root.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as? org.w3c.dom.Element ?: continue
                if (metaData.parentNode == service) {
                    val name = metaData.getAndroidAttribute("name")
                    val value = metaData.getAndroidAttribute("value")
                    if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                        val location = context.getLocation(metaData)
                        servicesWithMetadata.add(ServiceMetadataInfo(location, name, value))
                    }
                }
            }
        }

        val activityElements = root.getElementsByTagName("activity")
        for (i in 0 until activityElements.length) {
            val activity = activityElements.item(i) as? org.w3c.dom.Element ?: continue
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as? org.w3c.dom.Element ?: continue
                if (filter.parentNode != activity) continue

                val actions = mutableListOf<String>()
                val actionElements = filter.getElementsByTagName("action")
                for (k in 0 until actionElements.length) {
                    val action = actionElements.item(k) as? org.w3c.dom.Element ?: continue
                    val actionName = action.getAndroidAttribute("name")
                    if (actionName != null) {
                        actions.add(actionName)
                    }
                }

                val categories = mutableListOf<String>()
                val categoryElements = filter.getElementsByTagName("category")
                for (k in 0 until categoryElements.length) {
                    val category = categoryElements.item(k) as? org.w3c.dom.Element ?: continue
                    val categoryName = category.getAndroidAttribute("name")
                    if (categoryName != null) {
                        categories.add(categoryName)
                    }
                }

                activities.add(ActivityInfo(actions, categories))
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (servicesWithMetadata.isEmpty()) return

        val minSdkVersion = context.project.minSdkVersion.featureLevel
        val hasMatchingActivity = activities.any { activity ->
            val hasAction = activity.actions.contains("WATCH_FACE_EDITOR")
            if (hasAction) {
                if (minSdkVersion >= 30) {
                    true
                } else {
                    activity.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")
                }
            } else {
                false
            }
        }

        if (!hasMatchingActivity) {
            for (info in servicesWithMetadata) {
                val message = if (minSdkVersion < 30) {
                    "To support WATCH_FACE_EDITOR configuration action, there must be an activity with an intent filter for WATCH_FACE_EDITOR and category com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
                } else {
                    "To support WATCH_FACE_EDITOR configuration action, there must be an activity with an intent filter for WATCH_FACE_EDITOR"
                }
                context.report(
                    ISSUE,
                    info.location,
                    message
                )
            }
        }
    }
}

private fun org.w3c.dom.Element.getAndroidAttribute(localName: String): String? {
    val value = this.getAttributeNS("http://schemas.android.com/apk/res/android", localName)
    if (value.isNotEmpty()) return value
    val attr = this.getAttribute("android:$localName")
    return if (attr.isNotEmpty()) attr else null
}
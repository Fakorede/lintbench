package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be a corresponding configuration \
                activity in the same package that has an intent filter for `WATCH_FACE_EDITOR`. \
                If the `minSdkVersion` is less than 30, this intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val WEARABLE_CONFIGURATION_ACTION = 
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        private val WATCH_FACE_EDITOR_ACTIONS = setOf(
            "com.google.android.wearable.watchface.configuration.WATCH_FACE_CONFIG_ACTION",
            "WATCH_FACE_EDITOR"
        )

        private const val WEARABLE_CONFIGURATION_CATEGORY = 
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    }

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return
        
        val applications = root.getElementsByTagName("application")
        if (applications.length == 0) return
        val application = applications.item(0) as? org.w3c.dom.Element ?: return

        val services = application.getElementsByTagName("service")
        var hasWatchFaceServiceWithConfig = false

        for (i in 0 until services.length) {
            val service = services.item(i) as? org.w3c.dom.Element ?: continue
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as? org.w3c.dom.Element ?: continue
                val name = metaData.getAndroidAttribute("name")
                if (name == WEARABLE_CONFIGURATION_ACTION) {
                    val value = metaData.getAndroidAttribute("value")
                    if (value in WATCH_FACE_EDITOR_ACTIONS) {
                        hasWatchFaceServiceWithConfig = true
                        break
                    }
                }
            }
            if (hasWatchFaceServiceWithConfig) break
        }

        val minSdkVersion = context.mainProject.minSdkVersion.apiLevel

        val activities = application.getElementsByTagName("activity")
        var hasMatchingActivity = false

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
            if (matchesActivity(activity, minSdkVersion)) {
                hasMatchingActivity = true
                break
            }
        }

        val manifestFile = context.mainProject.manifestFiles.firstOrNull()
        val location = manifestFile?.let { Location.create(it) } ?: Location.create(context.project.dir)

        if (hasWatchFaceServiceWithConfig && !hasMatchingActivity) {
            val message = if (minSdkVersion < 30) {
                "A watch face service defines `wearableConfigurationAction` as `WATCH_FACE_EDITOR`, but no activity with a matching intent filter and the `WEARABLE_CONFIGURATION` category was found."
            } else {
                "A watch face service defines `wearableConfigurationAction` as `WATCH_FACE_EDITOR`, but no activity with a matching intent filter was found."
            }
            context.report(ISSUE, location, message)
        } else if (!hasWatchFaceServiceWithConfig && hasMatchingActivity) {
            val message = "An activity is configured as a watch face editor, but no watch face service defines the `wearableConfigurationAction` metadata."
            context.report(ISSUE, location, message)
        }
    }

    private fun matchesActivity(activity: org.w3c.dom.Element, minSdkVersion: Int): Boolean {
        val intentFilters = activity.getElementsByTagName("intent-filter")
        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as? org.w3c.dom.Element ?: continue
            
            val actions = filter.getElementsByTagName("action")
            var hasMatchingAction = false
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? org.w3c.dom.Element ?: continue
                val actionName = action.getAndroidAttribute("name")
                if (actionName in WATCH_FACE_EDITOR_ACTIONS) {
                    hasMatchingAction = true
                    break
                }
            }
            
            if (!hasMatchingAction) continue
            
            if (minSdkVersion < 30) {
                val categories = filter.getElementsByTagName("category")
                var hasMatchingCategory = false
                for (j in 0 until categories.length) {
                    val category = categories.item(j) as? org.w3c.dom.Element ?: continue
                    val categoryName = category.getAndroidAttribute("name")
                    if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                        hasMatchingCategory = true
                        break
                    }
                }
                if (hasMatchingCategory) {
                    return true
                }
            } else {
                return true
            }
        }
        return false
    }

    private fun org.w3c.dom.Element.getAndroidAttribute(localName: String): String {
        return this.getAttributeNS("http://schemas.android.com/apk/res/android", localName).takeIf { it.isNotEmpty() }
            ?: this.getAttribute("android:$localName")
    }
}
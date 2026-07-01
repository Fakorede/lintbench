package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TAG_MANIFEST = "manifest"
        private const val TAG_ACTIVITY = "activity"
        private const val TAG_INTENT_FILTER = "intent-filter"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
        private const val ATTR_NAME = "name"
        private const val ACTION_NAME = "WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != TAG_MANIFEST) return

        val minSdk = try {
            context.project.minSdkVersion?.apiLevel ?: 30
        } catch (e: Exception) {
            30
        }

        val activities = mutableListOf<Element>()
        val allActivities = element.getElementsByTagName(TAG_ACTIVITY)
        for (i in 0 until allActivities.length) {
            activities.add(allActivities.item(i) as Element)
        }

        val activitiesWithFilter = activities.filter { hasWearableConfigIntentFilter(it, minSdk) }

        if (activitiesWithFilter.size > 1) {
            for (i in 1 until activitiesWithFilter.size) {
                context.report(
                    ISSUE,
                    context.getLocation(activitiesWithFilter[i]),
                    "Duplicate watch face configuration activity found. Only one activity should handle the $ACTION_NAME intent."
                )
            }
        }
    }

    private fun getAndroidAttr(element: Element, attr: String): String {
        return element.getAttributeNS(ANDROID_URI, attr)?.takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:$attr")?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    private fun hasWearableConfigIntentFilter(activity: Element, minSdk: Int): Boolean {
        val filters = activity.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as Element
            var hasAction = false
            var hasCategory = minSdk >= 30

            val actions = filter.getElementsByTagName(TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as Element
                if (getAndroidAttr(action, ATTR_NAME) == ACTION_NAME) {
                    hasAction = true
                }
            }

            if (!hasAction) continue

            if (minSdk < 30) {
                val categories = filter.getElementsByTagName(TAG_CATEGORY)
                for (j in 0 until categories.length) {
                    val cat = categories.item(j) as Element
                    if (getAndroidAttr(cat, ATTR_NAME) == CATEGORY_NAME) {
                        hasCategory = true
                    }
                }
            }

            if (hasAction && hasCategory) return true
        }
        return false
    }
}
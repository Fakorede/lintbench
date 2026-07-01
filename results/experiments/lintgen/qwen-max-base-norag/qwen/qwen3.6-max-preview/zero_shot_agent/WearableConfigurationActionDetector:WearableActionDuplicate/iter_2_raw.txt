package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val TAG_MANIFEST = "manifest"
        private const val TAG_SERVICE = "service"
        private const val TAG_ACTIVITY = "activity"
        private const val TAG_META_DATA = "meta-data"
        private const val TAG_INTENT_FILTER = "intent-filter"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"
        private const val ATTR_NAME = "name"
        private const val ATTR_VALUE = "value"
        private const val METADATA_NAME = "wearableConfigurationAction"
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

        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        val allServices = element.getElementsByTagName(TAG_SERVICE)
        for (i in 0 until allServices.length) {
            services.add(allServices.item(i) as Element)
        }

        val allActivities = element.getElementsByTagName(TAG_ACTIVITY)
        for (i in 0 until allActivities.length) {
            activities.add(allActivities.item(i) as Element)
        }

        val servicesWithMeta = services.filter { hasWearableConfigMetadata(it) }
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

        val hasServiceMeta = servicesWithMeta.isNotEmpty()
        val hasActivityFilter = activitiesWithFilter.isNotEmpty()

        if (hasServiceMeta && !hasActivityFilter) {
            context.report(
                ISSUE,
                context.getLocation(servicesWithMeta[0]),
                "Watch face service defines $METADATA_NAME metadata but no matching configuration activity was found."
            )
        } else if (!hasServiceMeta && hasActivityFilter) {
            context.report(
                ISSUE,
                context.getLocation(activitiesWithFilter[0]),
                "Configuration activity found for $ACTION_NAME but no watch face service defines the corresponding $METADATA_NAME metadata."
            )
        }
    }

    private fun getAndroidAttr(element: Element, attr: String): String {
        val nsValue = element.getAttributeNS(ANDROID_URI, attr)
        if (nsValue.isNotEmpty()) return nsValue
        val prefixed = element.getAttribute("android:$attr")
        if (prefixed.isNotEmpty()) return prefixed
        return element.getAttribute(attr)
    }

    private fun hasWearableConfigMetadata(service: Element): Boolean {
        val metaDatas = service.getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metaDatas.length) {
            val meta = metaDatas.item(i) as Element
            val name = getAndroidAttr(meta, ATTR_NAME)
            val value = getAndroidAttr(meta, ATTR_VALUE)
            if (name == METADATA_NAME && value == ACTION_NAME) {
                return true
            }
        }
        return false
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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private const val TAG_SERVICE = "service"
        private const val TAG_ACTIVITY = "activity"
        private const val TAG_META_DATA = "meta-data"
        private const val TAG_INTENT_FILTER = "intent-filter"
        private const val TAG_ACTION = "action"
        private const val TAG_CATEGORY = "category"

        private const val ATTR_NAME = "name"
        private const val ATTR_VALUE = "value"

        private const val META_WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
        private const val META_WEARABLE_CONFIGURATION_ACTION_FQ =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares a wearableConfigurationAction, there must be a matching activity with an intent filter for that action. On devices running API level < 30, that intent filter must also include the WEARABLE_CONFIGURATION category.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val serviceActions = mutableListOf<Pair<String, Location>>()
    private val activityActions = mutableMapOf<String, Boolean>()

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_META_DATA, TAG_INTENT_FILTER)

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        when (element.tagName) {
            TAG_META_DATA -> visitMetaData(context, element)
            TAG_INTENT_FILTER -> visitIntentFilter(element)
        }
    }

    private fun visitMetaData(context: XmlContext, element: org.w3c.dom.Element) {
        val parent = element.parentNode as? org.w3c.dom.Element ?: return
        if (parent.tagName != TAG_SERVICE) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != META_WEARABLE_CONFIGURATION_ACTION &&
            name != META_WEARABLE_CONFIGURATION_ACTION_FQ
        ) {
            return
        }

        val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
        if (value.isNotBlank()) {
            serviceActions.add(value to context.getLocation(element))
        }
    }

    private fun visitIntentFilter(element: org.w3c.dom.Element) {
        val parent = element.parentNode as? org.w3c.dom.Element ?: return
        if (parent.tagName != TAG_ACTIVITY) return

        var action: String? = null
        var hasWearableCategory = false

        element.childNodes.forEachElement { child ->
            when (child.tagName) {
                TAG_ACTION -> {
                    if (action == null) {
                        action = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    }
                }
                TAG_CATEGORY -> {
                    if (child.getAttributeNS(ANDROID_URI, ATTR_NAME) ==
                        CATEGORY_WEARABLE_CONFIGURATION
                    ) {
                        hasWearableCategory = true
                    }
                }
            }
        }

        action?.let {
            activityActions[it] = activityActions.getOrDefault(it, false) || hasWearableCategory
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdk = context.mainProject.minSdkVersion.apiLevel

        for ((action, location) in serviceActions) {
            val hasCategory = activityActions[action]
            if (hasCategory == null) {
                context.report(
                    ISSUE,
                    location,
                    "No activity found with an intent filter for action '$action'"
                )
            } else if (minSdk < 30 && !hasCategory) {
                context.report(
                    ISSUE,
                    location,
                    "The activity handling action '$action' must include the category " +
                        "'$CATEGORY_WEARABLE_CONFIGURATION' when minSdkVersion is less than 30"
                )
            }
        }

        serviceActions.clear()
        activityActions.clear()
    }

    private inline fun org.w3c.dom.NodeList.forEachElement(
        action: (org.w3c.dom.Element) -> Unit
    ) {
        for (i in 0 until length) {
            (item(i) as? org.w3c.dom.Element)?.let(action)
        }
    }
}
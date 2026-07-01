package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val servicesWithConfig = mutableListOf<Element>()
    private val candidateConfigActivities = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasWearableConfigurationAction(element)) {
                    servicesWithConfig.add(element)
                }
            }
            TAG_ACTIVITY -> {
                if (isConfigurationActivity(context, element)) {
                    candidateConfigActivities.add(element)
                }
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        if (servicesWithConfig.isNotEmpty() && candidateConfigActivities.size > 1) {
            val sorted = candidateConfigActivities.sortedBy {
                context.getLocation(it).start?.line ?: 0
            }
            for (i in 1 until sorted.size) {
                context.report(
                    ISSUE,
                    sorted[i],
                    context.getLocation(sorted[i]),
                    "Duplicate watch face configuration activity found; only one activity should handle `$WATCH_FACE_EDITOR_ACTION`"
                )
            }
        }

        servicesWithConfig.clear()
        candidateConfigActivities.clear()
    }

    private fun hasWearableConfigurationAction(service: Element): Boolean {
        val metaDataList = service.getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metaDataList.length) {
            val meta = metaDataList.item(i) as? Element ?: continue
            val name = meta.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name != META_DATA_WEARABLE_CONFIGURATION_ACTION) continue

            val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (value == WATCH_FACE_EDITOR_ACTION) {
                return true
            }
        }
        return false
    }

    private fun isConfigurationActivity(context: XmlContext, activity: Element): Boolean {
        val minSdk = context.mainProject.minSdkVersion.apiLevel
        val filters = activity.getElementsByTagName(TAG_INTENT_FILTER)

        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue

            var hasAction = false
            var hasCategory = false

            val actions = filter.getElementsByTagName(TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? Element ?: continue
                if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION) {
                    hasAction = true
                }
            }

            val categories = filter.getElementsByTagName(TAG_CATEGORY)
            for (j in 0 until categories.length) {
                val category = categories.item(j) as? Element ?: continue
                if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == CATEGORY_WEARABLE_CONFIGURATION) {
                    hasCategory = true
                }
            }

            if (hasAction && (minSdk >= 30 || hasCategory)) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val META_DATA_WEARABLE_CONFIGURATION_ACTION =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                A watch face service that declares the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR` must have exactly one activity that handles \
                that action. Declaring more than one configuration activity is an error because \
                the system cannot determine which one to launch.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
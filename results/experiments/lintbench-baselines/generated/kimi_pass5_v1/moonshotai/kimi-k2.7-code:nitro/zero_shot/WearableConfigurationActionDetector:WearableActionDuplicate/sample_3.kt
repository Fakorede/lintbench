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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val serviceActions = mutableListOf<Pair<Element, String>>()
    private val candidateActivities = mutableListOf<Pair<Element, String>>()

    override fun appliesTo(context: Context): Collection<Scope> = Scope.MANIFEST_SCOPE

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun beforeCheckFile(context: Context) {
        serviceActions.clear()
        candidateActivities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                val action = element.getMetadataValue(WEARABLE_CONFIGURATION_ACTION)
                if (action == WATCH_FACE_EDITOR) {
                    serviceActions.add(element to action)
                }
            }
            TAG_ACTIVITY -> {
                val action = element.findWatchFaceEditorAction(context)
                if (action != null) {
                    candidateActivities.add(element to action)
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (serviceActions.isEmpty()) return

        val xmlContext = context as XmlContext
        val activityMap = candidateActivities.groupBy({ it.second }, { it.first })

        for ((_, action) in serviceActions) {
            val matches = activityMap[action] ?: emptyList()
            if (matches.size > 1) {
                for (activity in matches) {
                    xmlContext.report(
                        ISSUE,
                        activity,
                        xmlContext.getLocation(activity),
                        "Duplicate watch face configuration activities found for action `$action`"
                    )
                }
            }
        }
    }

    private fun Element.getMetadataValue(name: String): String? {
        val nodes = getElementsByTagName(TAG_META_DATA)
        for (i in 0 until nodes.length) {
            val meta = nodes.item(i) as? Element ?: continue
            if (meta.getAttributeNS(ANDROID_URI, ATTR_NAME) == name) {
                val value = meta.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun Element.findWatchFaceEditorAction(context: XmlContext): String? {
        val filters = getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue
            val actions = filter.getElementsByTagName(TAG_ACTION)
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? Element ?: continue
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName == WATCH_FACE_EDITOR) {
                    if (context.mainProject.minSdkVersion.featureLevel < 30) {
                        if (!filter.hasCategory(WEARABLE_CONFIGURATION_CATEGORY)) continue
                    }
                    return actionName
                }
            }
        }
        return null
    }

    private fun Element.hasCategory(categoryName: String): Boolean {
        val categories = getElementsByTagName(TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val cat = categories.item(i) as? Element ?: continue
            if (cat.getAttributeNS(ANDROID_URI, ATTR_NAME) == categoryName) return true
        }
        return false
    }

    companion object {
        private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE: Issue = Issue.create(
            "WearableActionDuplicate",
            "Duplicate watch face configuration activities found",
            """
                If and only if a watch face service defines `wearableConfigurationAction` \
                metadata with the value `WATCH_FACE_EDITOR`, there should be exactly one \
                activity in the same package with an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if minSdkVersion is less than 30).
                """,
            Category.WEARABLE,
            6,
            Severity.ERROR,
            Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
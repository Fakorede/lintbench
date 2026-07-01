package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
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
import com.android.tools.lint.detector.api.XmlElement
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    private val servicesNeedingConfig = mutableListOf<XmlElement>()
    private val configActivities = mutableListOf<ConfigActivityInfo>()

    private data class ConfigActivityInfo(
        val element: XmlElement,
        val hasCategory: Boolean
    )

    companion object {
        private const val METADATA_NAME = "wearableConfigurationAction"
        private const val ACTION_NAME = "WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, " +
                    "there should be exactly one activity in the same package with an intent filter for WATCH_FACE_EDITOR " +
                    "(and the WEARABLE_CONFIGURATION category if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: XmlElement) {
        when (element.tagName) {
            TAG_SERVICE -> {
                val hasConfigMetadata = element.getSubTags().any { meta ->
                    meta.tagName == TAG_META_DATA &&
                            meta.getAttribute(ATTR_NAME) == METADATA_NAME &&
                            meta.getAttribute(ATTR_VALUE) == ACTION_NAME
                }
                if (hasConfigMetadata) {
                    servicesNeedingConfig.add(element)
                }
            }
            TAG_ACTIVITY -> {
                for (filter in element.getSubTags().filter { it.tagName == TAG_INTENT_FILTER }) {
                    val actions = filter.getSubTags()
                        .filter { it.tagName == TAG_ACTION }
                        .mapNotNull { it.getAttribute(ATTR_NAME) }
                        .toSet()

                    if (ACTION_NAME in actions) {
                        val categories = filter.getSubTags()
                            .filter { it.tagName == TAG_CATEGORY }
                            .mapNotNull { it.getAttribute(ATTR_NAME) }
                            .toSet()
                        configActivities.add(ConfigActivityInfo(element, CATEGORY_NAME in categories))
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context.file.name != ANDROID_MANIFEST_XML || context !is XmlContext) return

        val minSdk = context.mainProject.minSdkVersion
        val requireCategory = minSdk < 30

        for (serviceElement in servicesNeedingConfig) {
            val matchingActivities = configActivities.filter { activityInfo ->
                !requireCategory || activityInfo.hasCategory
            }

            when (matchingActivities.size) {
                0 -> context.report(
                    ISSUE,
                    context.getLocation(serviceElement),
                    "Missing watch face configuration activity for action $ACTION_NAME"
                )
                1 -> { /* Valid configuration */ }
                else -> context.report(
                    ISSUE,
                    context.getLocation(serviceElement),
                    "Duplicate watch face configuration activities found for action $ACTION_NAME"
                )
            }
        }

        servicesNeedingConfig.clear()
        configActivities.clear()
    }
}
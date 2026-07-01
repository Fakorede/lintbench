package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
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

    private val metadataElements = mutableListOf<Element>()
    private val intentFilters = mutableListOf<Element>()

    companion object {
        private const val WEARABLE_CONFIG_ACTION = "wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be an activity in the same \
                package with an intent filter for `WATCH_FACE_EDITOR`. If `minSdkVersion` \
                is less than 30, the intent filter must also include the category \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_META_DATA, TAG_INTENT_FILTER)
    }

    override fun beforeCheckFile(context: Context) {
        metadataElements.clear()
        intentFilters.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIG_ACTION && value == WATCH_FACE_EDITOR) {
                    metadataElements.add(element)
                }
            }
            TAG_INTENT_FILTER -> {
                val actions = element.getElementsByTagName(TAG_ACTION)
                for (i in 0 until actions.length) {
                    val action = actions.item(i) as Element
                    if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR) {
                        intentFilters.add(element)
                        break
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (metadataElements.isEmpty()) return
        val ctx = context as XmlContext

        val minSdk = ctx.mainProject.minSdkVersion
        var validFound = false
        val missingCategoryFilters = mutableListOf<Element>()

        for (filter in intentFilters) {
            val categories = filter.getElementsByTagName(TAG_CATEGORY)
            var hasConfigCategory = false
            for (i in 0 until categories.length) {
                val cat = categories.item(i) as Element
                if (cat.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY) {
                    hasConfigCategory = true
                    break
                }
            }
            if (minSdk >= 30 || hasConfigCategory) {
                validFound = true
            } else {
                missingCategoryFilters.add(filter)
            }
        }

        if (validFound) return

        if (missingCategoryFilters.isNotEmpty()) {
            for (filter in missingCategoryFilters) {
                ctx.report(
                    ISSUE,
                    filter,
                    ctx.getLocation(filter),
                    "Intent filter for `$WATCH_FACE_EDITOR` must include category " +
                    "`$WEARABLE_CONFIGURATION_CATEGORY` when `minSdkVersion` is less than 30"
                )
            }
        } else {
            for (meta in metadataElements) {
                ctx.report(
                    ISSUE,
                    meta,
                    ctx.getLocation(meta),
                    "No activity found with an intent filter for `$WATCH_FACE_EDITOR`. " +
                    "Add an activity with the corresponding intent filter to handle watch face configuration."
                )
            }
        }
    }
}
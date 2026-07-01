package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        
        val services = root.getElementsByTagName("service")
        var metadataElement: Element? = null
        var hasWatchFaceEditorMetadata = false

        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            val metaDatas = service.getElementsByTagName("meta-data")
            for (j in 0 until metaDatas.length) {
                val metaData = metaDatas.item(j) as Element
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, "value")
                if ((name == METADATA_NAME || name.endsWith("wearableConfigurationAction")) &&
                    (value == ACTION_VALUE || value.endsWith("WATCH_FACE_EDITOR"))) {
                    hasWatchFaceEditorMetadata = true
                    metadataElement = metaData
                    break
                }
            }
            if (hasWatchFaceEditorMetadata) {
                break
            }
        }

        if (!hasWatchFaceEditorMetadata) {
            return
        }

        val activities = root.getElementsByTagName("activity")
        var foundAction = false
        var foundCategoryForAction = false

        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val filter = intentFilters.item(j) as Element
                val actions = filter.getElementsByTagName("action")
                var filterHasAction = false
                for (k in 0 until actions.length) {
                    val action = actions.item(k) as Element
                    val actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                    if (actionName == ACTION_VALUE || actionName.endsWith("WATCH_FACE_EDITOR")) {
                        filterHasAction = true
                        break
                    }
                }

                if (filterHasAction) {
                    foundAction = true
                    val categories = filter.getElementsByTagName("category")
                    for (k in 0 until categories.length) {
                        val category = categories.item(k) as Element
                        val categoryName = category.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                        if (categoryName == CATEGORY_VALUE || categoryName.endsWith("WEARABLE_CONFIGURATION")) {
                            foundCategoryForAction = true
                            break
                        }
                    }
                }
            }
        }

        val minSdkVersion = context.project.minSdkVersion.apiLevel
        val needsCategory = minSdkVersion < 30

        val locationNode = metadataElement ?: root

        if (!foundAction) {
            context.report(
                ISSUE,
                locationNode,
                context.getLocation(locationNode),
                "An activity with an intent-filter for action `$ACTION_VALUE` must be configured to match the wearableConfigurationAction metadata."
            )
        } else if (needsCategory && !foundCategoryForAction) {
            context.report(
                ISSUE,
                locationNode,
                context.getLocation(locationNode),
                "The intent-filter with action `$ACTION_VALUE` must also include the category `$CATEGORY_VALUE` when minSdkVersion is less than 30."
            )
        }
    }

    companion object {
        private const val METADATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION_VALUE = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val CATEGORY_VALUE = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata with `WATCH_FACE_EDITOR`, \
                there must be a corresponding activity in the same package with an intent filter for `WATCH_FACE_EDITOR`. \
                If the minSdkVersion is less than 30, this intent filter must also include the `WEARABLE_CONFIGURATION` category.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
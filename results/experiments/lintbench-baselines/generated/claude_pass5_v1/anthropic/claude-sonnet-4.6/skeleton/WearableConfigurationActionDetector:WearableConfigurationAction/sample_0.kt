package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same \
                package, which has an intent filter for `WATCH_FACE_EDITOR` (with \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                `minSdkVersion` is less than 30).
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val WATCH_FACE_EDITOR_METADATA_VALUE =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"

        private const val KEY_SERVICE_LOCATION = "serviceLocation"
        private const val KEY_HAS_EDITOR_ACTION = "hasEditorAction"
        private const val KEY_HAS_WEARABLE_CATEGORY = "hasWearableCategory"
    }

    /**
     * Tracks whether we found a watch face service with wearableConfigurationAction = WATCH_FACE_EDITOR.
     * Stores the location of that metadata element for reporting.
     */
    private var watchFaceServiceMetaDataLocation: Location? = null

    /**
     * Tracks whether we found an activity with WATCH_FACE_EDITOR intent filter action.
     */
    private var foundEditorActivity = false

    /**
     * Tracks whether the editor activity also has the WEARABLE_CONFIGURATION category.
     */
    private var editorActivityHasWearableCategory = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> checkService(context, element)
            TAG_ACTIVITY -> checkActivity(context, element)
        }
    }

    private fun checkService(context: XmlContext, serviceElement: Element) {
        // Look for meta-data child with wearableConfigurationAction = WATCH_FACE_EDITOR
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_METADATA_VALUE
                ) {
                    watchFaceServiceMetaDataLocation = context.getLocation(child)
                }
            }
        }
    }

    private fun checkActivity(context: XmlContext, activityElement: Element) {
        // Look for intent-filter children that have WATCH_FACE_EDITOR action
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                var hasEditorAction = false
                var hasWearableCategory = false
                val filterChildren = child.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j) as? Element ?: continue
                    when (filterChild.tagName) {
                        TAG_ACTION -> {
                            val actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                hasEditorAction = true
                            }
                        }
                        TAG_CATEGORY -> {
                            val categoryName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
                            if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                hasWearableCategory = true
                            }
                        }
                    }
                }
                if (hasEditorAction) {
                    foundEditorActivity = true
                    if (hasWearableCategory) {
                        editorActivityHasWearableCategory = true
                    }
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val metaDataLocation = watchFaceServiceMetaDataLocation ?: return

        val minSdk = context.mainProject.minSdk

        if (!foundEditorActivity) {
            // No activity with WATCH_FACE_EDITOR action at all
            context.report(
                ISSUE,
                metaDataLocation,
                "Watch face service has `wearableConfigurationAction` metadata with value " +
                    "`WATCH_FACE_EDITOR`, but no activity in the package has an intent filter " +
                    "for action `$WATCH_FACE_EDITOR_ACTION`"
            )
        } else if (minSdk < 30 && !editorActivityHasWearableCategory) {
            // Activity found but missing the required category for API < 30
            context.report(
                ISSUE,
                metaDataLocation,
                "Watch face service has `wearableConfigurationAction` metadata with value " +
                    "`WATCH_FACE_EDITOR`, but the activity with `$WATCH_FACE_EDITOR_ACTION` " +
                    "intent filter is missing the " +
                    "`$WEARABLE_CONFIGURATION_CATEGORY` category (required when " +
                    "`minSdkVersion` < 30)"
            )
        }
    }
}
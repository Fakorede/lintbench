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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        if (root.tagName != SdkConstants.TAG_MANIFEST) return

        val manifestPackage = root.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PACKAGE)
        val application = root.getElementsByTagName(SdkConstants.TAG_APPLICATION).item(0) as? Element
            ?: return

        val watchFaceServices = mutableListOf<Pair<Element, String>>()
        val services = application.getElementsByTagName(SdkConstants.TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val metaData = service.findMetaData(METADATA_NAME) ?: continue
            val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                .takeIf { it.isNotEmpty() } ?: continue

            if (value == WATCH_FACE_EDITOR_ACTION || value == WATCH_FACE_EDITOR_SHORT) {
                watchFaceServices.add(service to resolveComponentPackage(service, manifestPackage))
            }
        }

        if (watchFaceServices.isEmpty()) return

        val activityFilters = mutableMapOf<String, MutableList<Element>>()
        collectWatchFaceEditorActivities(
            application,
            SdkConstants.TAG_ACTIVITY,
            manifestPackage,
            activityFilters
        )
        collectWatchFaceEditorActivities(
            application,
            SdkConstants.TAG_ACTIVITY_ALIAS,
            manifestPackage,
            activityFilters
        )

        val minSdk = context.project.minSdk

        for ((service, servicePackage) in watchFaceServices) {
            val filters = activityFilters[servicePackage]
            val metaData = service.findMetaData(METADATA_NAME)!!

            if (filters.isNullOrEmpty()) {
                context.report(
                    ISSUE,
                    metaData,
                    context.getLocation(metaData),
                    "Wearable configuration action `$WATCH_FACE_EDITOR_ACTION` must be handled by an activity in the same package."
                )
                continue
            }

            if (minSdk < 30) {
                for (intentFilter in filters) {
                    if (!intentFilter.hasCategory(WEARABLE_CONFIGURATION_CATEGORY)) {
                        context.report(
                            ISSUE,
                            intentFilter,
                            context.getLocation(intentFilter),
                            "Intent filter handling `$WATCH_FACE_EDITOR_ACTION` must include `<category android:name=\"$WEARABLE_CONFIGURATION_CATEGORY\" />` because `minSdkVersion` is $minSdk (< 30)."
                        )
                    }
                }
            }
        }
    }

    private fun collectWatchFaceEditorActivities(
        application: Element,
        tagName: String,
        manifestPackage: String,
        result: MutableMap<String, MutableList<Element>>
    ) {
        val elements = application.getElementsByTagName(tagName)
        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            val packageName = resolveComponentPackage(element, manifestPackage)

            val intentFilters = element.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER)
            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as? Element ?: continue
                if (intentFilter.hasAction(WATCH_FACE_EDITOR_ACTION)) {
                    result.getOrPut(packageName) { mutableListOf() }.add(intentFilter)
                    break
                }
            }
        }
    }

    private fun Element.findMetaData(name: String): Element? {
        val metaDataElements = getElementsByTagName(SdkConstants.TAG_META_DATA)
        for (i in 0 until metaDataElements.length) {
            val metaData = metaDataElements.item(i) as? Element ?: continue
            val metaName = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (metaName == name) {
                return metaData
            }
        }
        return null
    }

    private fun Element.hasAction(actionName: String): Boolean {
        val actions = getElementsByTagName(SdkConstants.TAG_ACTION)
        for (i in 0 until actions.length) {
            val action = actions.item(i) as? Element ?: continue
            val name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == actionName) {
                return true
            }
        }
        return false
    }

    private fun Element.hasCategory(categoryName: String): Boolean {
        val categories = getElementsByTagName(SdkConstants.TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == categoryName) {
                return true
            }
        }
        return false
    }

    private fun resolveComponentPackage(element: Element, manifestPackage: String): String {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        return when {
            name.isEmpty() -> manifestPackage
            name.startsWith(".") -> manifestPackage
            name.contains(".") -> name.substringBeforeLast(".")
            else -> manifestPackage
        }
    }

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "com.google.android.wearable.watchface.WATCH_FACE_EDITOR"
        private const val WATCH_FACE_EDITOR_SHORT = "WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val METADATA_NAME = "wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service declares `wearableConfigurationAction` metadata with the value \
                `com.google.android.wearable.watchface.WATCH_FACE_EDITOR`, there must be an activity in the \
                same package whose intent filter handles that action. If `minSdkVersion` is less than 30, \
                the intent filter must also include the \
                `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
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
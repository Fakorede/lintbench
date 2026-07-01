package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        private const val META_DATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION_VALUE = "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR"
        private const val CATEGORY_VALUE = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                When a watch face service defines the `wearableConfigurationAction` metadata \
                with the value `WATCH_FACE_EDITOR`, there must be an activity in the same package \
                that has an intent filter for `WATCH_FACE_EDITOR`.
                
                If the `minSdkVersion` is less than 30, this intent filter must also include \
                the category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`.
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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val applications = root.subElements("application")
        
        for (app in applications) {
            val services = app.subElements("service")
            val matchingMetadataElements = mutableListOf<Element>()

            for (service in services) {
                val metadataList = service.subElements("meta-data")
                for (meta in metadataList) {
                    val name = meta.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                    val value = meta.getAttributeNS(SdkConstants.ANDROID_URI, "value")
                    if (name == META_DATA_NAME && value == ACTION_VALUE) {
                        matchingMetadataElements.add(meta)
                    }
                }
            }

            if (matchingMetadataElements.isEmpty()) {
                continue
            }

            val activities = app.subElements("activity")
            val minSdkVersion = context.project.minSdkVersion.apiLevel
            val needsCategory = minSdkVersion < 30
            var hasValidActivity = false

            for (activity in activities) {
                val intentFilters = activity.subElements("intent-filter")
                for (filter in intentFilters) {
                    val actions = filter.subElements("action")
                    val categories = filter.subElements("category")

                    val hasAction = actions.any {
                        it.getAttributeNS(SdkConstants.ANDROID_URI, "name") == ACTION_VALUE
                    }

                    if (hasAction) {
                        if (needsCategory) {
                            val hasCategory = categories.any {
                                it.getAttributeNS(SdkConstants.ANDROID_URI, "name") == CATEGORY_VALUE
                            }
                            if (hasCategory) {
                                hasValidActivity = true
                                break
                            }
                        } else {
                            hasValidActivity = true
                            break
                        }
                    }
                }
                if (hasValidActivity) {
                    break
                }
            }

            if (!hasValidActivity) {
                val message = if (needsCategory) {
                    "To support this wearable configuration action, there must be an activity with an intent-filter containing action `$ACTION_VALUE` and category `$CATEGORY_VALUE`."
                } else {
                    "To support this wearable configuration action, there must be an activity with an intent-filter containing action `$ACTION_VALUE`."
                }

                for (metaElement in matchingMetadataElements) {
                    context.report(
                        ISSUE,
                        metaElement,
                        context.getLocation(metaElement),
                        message
                    )
                }
            }
        }
    }

    private fun Element.subElements(tagName: String): List<Element> {
        val list = mutableListOf<Element>()
        val children = this.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) {
                list.add(node)
            }
        }
        return list
    }
}
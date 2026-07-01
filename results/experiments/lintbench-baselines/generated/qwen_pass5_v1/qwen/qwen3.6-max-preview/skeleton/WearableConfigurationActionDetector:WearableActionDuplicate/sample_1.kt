package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val META_DATA_NAME_SUFFIX = "wearableConfigurationAction"
        private const val ACTION_WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
        private const val CATEGORY_WEARABLE_CONFIGURATION = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with a matching intent filter. If minSdkVersion is less than 30, the intent filter must also include the WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = emptyList()

    override fun checkMergedProject(context: Context) {
        val manifest = context.project.mergedManifest ?: return
        val root = manifest.documentElement ?: return
        if (root.nodeName != "manifest") return

        val minSdk = context.project.minSdkVersion
        val requiresCategory = minSdk < 30

        val services = getChildren(root, "service")
        var hasEditorMetadata = false

        for (service in services) {
            val metaDataList = getChildren(service, "meta-data")
            for (metaData in metaDataList) {
                val name = metaData.getAttributeNS(ANDROID_URI, "name")
                val value = metaData.getAttributeNS(ANDROID_URI, "value")
                if (name.endsWith(META_DATA_NAME_SUFFIX) && value == ACTION_WATCH_FACE_EDITOR) {
                    hasEditorMetadata = true
                    break
                }
            }
            if (hasEditorMetadata) break
        }

        if (!hasEditorMetadata) return

        val activities = getChildren(root, "activity")
        var matchingActivityCount = 0

        for (activity in activities) {
            val intentFilters = getChildren(activity, "intent-filter")
            for (intentFilter in intentFilters) {
                val actions = getChildren(intentFilter, "action")
                val categories = getChildren(intentFilter, "category")

                var hasAction = false
                for (action in actions) {
                    if (action.getAttributeNS(ANDROID_URI, "name") == ACTION_WATCH_FACE_EDITOR) {
                        hasAction = true
                        break
                    }
                }

                if (!hasAction) continue

                var hasCategory = !requiresCategory
                if (requiresCategory) {
                    for (category in categories) {
                        if (category.getAttributeNS(ANDROID_URI, "name") == CATEGORY_WEARABLE_CONFIGURATION) {
                            hasCategory = true
                            break
                        }
                    }
                }

                if (hasCategory) {
                    matchingActivityCount++
                }
            }
        }

        if (matchingActivityCount != 1) {
            val message = if (matchingActivityCount == 0) {
                "Missing watch face configuration activity for WATCH_FACE_EDITOR"
            } else {
                "Duplicate watch face configuration activities found"
            }
            context.report(ISSUE, Location.create(context.file), message)
        }
    }

    private fun getChildren(parent: Node, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                result.add(child as Element)
            }
            child = child.nextSibling
        }
        return result
    }
}
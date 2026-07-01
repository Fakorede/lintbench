package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with an intent filter for WATCH_FACE_EDITOR. Duplicates can cause undefined behavior when configuring the watch face.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): List<String> = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isMergedManifest) return

        val appNode = element.getElementsByTagName("application").item(0) as? Element ?: return
        val minSdk = context.project.minSdkVersion?.apiLevel ?: 1

        val configActivities = mutableListOf<Element>()
        var hasWatchFaceService = false

        val children = appNode.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            when (node.tagName) {
                "service" -> {
                    val metaDatas = node.getElementsByTagName("meta-data")
                    for (j in 0 until metaDatas.length) {
                        val meta = metaDatas.item(j) as? Element ?: continue
                        val name = meta.getAttributeNS(ANDROID_URI, "name")
                        val value = meta.getAttributeNS(ANDROID_URI, "value")
                        if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
                            hasWatchFaceService = true
                        }
                    }
                }
                "activity" -> {
                    val intentFilters = node.getElementsByTagName("intent-filter")
                    for (j in 0 until intentFilters.length) {
                        val filter = intentFilters.item(j) as? Element ?: continue
                        val actions = filter.getElementsByTagName("action")
                        val categories = filter.getElementsByTagName("category")
                        var hasAction = false
                        var hasCategory = false
                        for (k in 0 until actions.length) {
                            val action = actions.item(k) as? Element ?: continue
                            if (action.getAttributeNS(ANDROID_URI, "name") == "WATCH_FACE_EDITOR") {
                                hasAction = true
                            }
                        }
                        for (k in 0 until categories.length) {
                            val cat = categories.item(k) as? Element ?: continue
                            if (cat.getAttributeNS(ANDROID_URI, "name") == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                                hasCategory = true
                            }
                        }
                        if (hasAction && (minSdk >= 30 || hasCategory)) {
                            configActivities.add(node)
                        }
                    }
                }
            }
        }

        if (hasWatchFaceService && configActivities.size > 1) {
            for (activity in configActivities) {
                val location = context.getLocation(activity)
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activities found"
                )
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        // Manifest analysis is performed in visitElement when the merged manifest is visited.
        super.checkMergedProject(context)
    }
}
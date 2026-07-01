package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val METADATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val ACTION_SUFFIX = "WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with a matching intent filter. Multiple activities handling the same configuration action can cause undefined behavior or launch the wrong configuration screen.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val xmlContext = context as? XmlContext ?: return
        val root = xmlContext.document.documentElement ?: return
        val minSdk = context.mainProject.minSdkVersion

        val appNodes = getChildren(root, "application")
        if (appNodes.isEmpty()) return
        val app = appNodes.first()

        val services = getChildren(app, "service")
        val targetActions = mutableSetOf<String>()

        for (service in services) {
            for (meta in getChildren(service, "meta-data")) {
                val name = meta.getAttributeNS(ANDROID_URI, "name")
                val value = meta.getAttributeNS(ANDROID_URI, "value")
                if (name == METADATA_NAME && value.endsWith(ACTION_SUFFIX)) {
                    targetActions.add(value)
                }
            }
        }

        if (targetActions.isEmpty()) return

        val activities = getChildren(app, "activity")
        val matchingActivities = mutableListOf<org.w3c.dom.Element>()

        for (activity in activities) {
            for (filter in getChildren(activity, "intent-filter")) {
                val actions = getChildren(filter, "action")
                val categories = getChildren(filter, "category")

                val hasTargetAction = actions.any { action ->
                    val actionName = action.getAttributeNS(ANDROID_URI, "name")
                    actionName in targetActions || actionName.endsWith(ACTION_SUFFIX)
                }

                if (!hasTargetAction) continue

                val hasRequiredCategory = if (minSdk < 30) {
                    categories.any { cat ->
                        cat.getAttributeNS(ANDROID_URI, "name") == CATEGORY_NAME
                    }
                } else {
                    true
                }

                if (hasRequiredCategory) {
                    matchingActivities.add(activity)
                    break
                }
            }
        }

        if (matchingActivities.size > 1) {
            for (i in 1 until matchingActivities.size) {
                val activity = matchingActivities[i]
                val location = xmlContext.getLocation(activity)
                context.report(
                    ISSUE,
                    location,
                    "Duplicate watch face configuration activity found for action $ACTION_SUFFIX"
                )
            }
        }
    }

    private fun getChildren(parent: org.w3c.dom.Node, tagName: String): List<org.w3c.dom.Element> {
        val result = mutableListOf<org.w3c.dom.Element>()
        var child = parent.firstChild
        while (child != null) {
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE && child.nodeName == tagName) {
                result.add(child as org.w3c.dom.Element)
            }
            child = child.nextSibling
        }
        return result
    }
}
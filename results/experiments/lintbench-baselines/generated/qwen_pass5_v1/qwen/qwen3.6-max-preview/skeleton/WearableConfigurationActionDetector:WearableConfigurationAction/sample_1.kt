package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

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
            explanation = "When a watch face service defines wearableConfigurationAction metadata, " +
                "there must be a corresponding activity in the same package with an intent filter " +
                "for that action. If minSdkVersion is less than 30, the intent filter must also " +
                "include the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val METADATA_NAME = "wearableConfigurationAction"
        private const val FULL_METADATA_NAME = "com.google.android.wearable.watchface.wearableConfigurationAction"
        private const val CATEGORY_WEARABLE_CONFIGURATION = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun checkMergedProject(context: Context) {
        val manifestFile = context.project.manifest ?: return
        val document = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        } catch (e: Exception) {
            return
        }

        val root = document.documentElement ?: return
        val application = getFirstChild(root, "application") ?: return

        val minSdk = context.project.minSdkVersion
        val requireCategory = minSdk < 30

        // Collect valid actions from activities that satisfy the intent filter requirements
        val validActions = mutableSetOf<String>()
        for (activity in getChildren(application, "activity")) {
            for (intentFilter in getChildren(activity, "intent-filter")) {
                var actionName: String? = null
                var hasCategory = !requireCategory

                for (child in getChildren(intentFilter, "action")) {
                    val name = child.getAttributeNS(ANDROID_URI, "name")
                    if (name.isNotEmpty()) {
                        actionName = name
                    }
                }

                if (requireCategory) {
                    for (child in getChildren(intentFilter, "category")) {
                        if (child.getAttributeNS(ANDROID_URI, "name") == CATEGORY_WEARABLE_CONFIGURATION) {
                            hasCategory = true
                        }
                    }
                }

                if (actionName != null && hasCategory) {
                    validActions.add(actionName)
                }
            }
        }

        // Check services for the wearableConfigurationAction metadata
        for (service in getChildren(application, "service")) {
            for (metaData in getChildren(service, "meta-data")) {
                val metaName = metaData.getAttributeNS(ANDROID_URI, "name")
                if (metaName == METADATA_NAME || metaName == FULL_METADATA_NAME) {
                    val metaValue = metaData.getAttributeNS(ANDROID_URI, "value")
                    if (metaValue.isNotEmpty() && !validActions.contains(metaValue)) {
                        val location = context.getLocation(metaData)
                        val categoryMsg = if (requireCategory) {
                            " and category \"$CATEGORY_WEARABLE_CONFIGURATION\""
                        } else {
                            ""
                        }
                        context.report(
                            ISSUE,
                            location,
                            "No activity found with intent filter action \"$metaValue\"$categoryMsg"
                        )
                    }
                }
            }
        }
    }

    private fun getFirstChild(parent: Node, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                return child as Element
            }
        }
        return null
    }

    private fun getChildren(parent: Node, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                result.add(child as Element)
            }
        }
        return result
    }
}
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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ACTION_NAME = "WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val META_NAME = "wearableConfigurationAction"

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package with a matching intent filter. Duplicates or missing activities cause configuration failures.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val manifestFile = context.project.mergedManifest ?: return
        val document = try {
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        } catch (e: Exception) {
            return
        }
        val root = document.documentElement ?: return
        val app = getChildren(root, "application").firstOrNull() ?: return
        val minSdk = context.project.minSdkVersion ?: 1

        val services = getChildren(app, "service")
        for (service in services) {
            if (hasWearableConfigMetadata(service)) {
                val activities = getChildren(app, "activity")
                val matchingCount = activities.count { hasMatchingIntentFilter(it, minSdk) }

                val location = Location.create(manifestFile)
                if (matchingCount > 1) {
                    context.report(ISSUE, location, "Duplicate watch face configuration activities found")
                } else if (matchingCount == 0) {
                    context.report(ISSUE, location, "Missing watch face configuration activity for $ACTION_NAME")
                }
            }
        }
    }

    private fun hasWearableConfigMetadata(service: org.w3c.dom.Element): Boolean {
        return getChildren(service, "meta-data").any { meta ->
            meta.getAttributeNS(ANDROID_URI, "name") == META_NAME &&
            meta.getAttributeNS(ANDROID_URI, "value") == ACTION_NAME
        }
    }

    private fun hasMatchingIntentFilter(activity: org.w3c.dom.Element, minSdk: Int): Boolean {
        return getChildren(activity, "intent-filter").any { filter ->
            val hasAction = getChildren(filter, "action").any {
                it.getAttributeNS(ANDROID_URI, "name") == ACTION_NAME
            }
            if (!hasAction) return@any false

            if (minSdk < 30) {
                getChildren(filter, "category").any {
                    it.getAttributeNS(ANDROID_URI, "name") == CATEGORY_NAME
                }
            } else {
                true
            }
        }
    }

    private fun getChildren(parent: org.w3c.dom.Node, tagName: String): List<org.w3c.dom.Element> {
        val list = mutableListOf<org.w3c.dom.Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE && node.nodeName == tagName) {
                list.add(node as org.w3c.dom.Element)
            }
        }
        return list
    }
}
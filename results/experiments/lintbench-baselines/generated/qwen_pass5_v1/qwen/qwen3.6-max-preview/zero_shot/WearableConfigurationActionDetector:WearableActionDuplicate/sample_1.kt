package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    companion object {
        private const val METADATA_NAME = "wearableConfigurationAction"
        private const val METADATA_VALUE = "WATCH_FACE_EDITOR"
        private const val ACTION_NAME = "WATCH_FACE_EDITOR"
        private const val CATEGORY_NAME = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        val ISSUE = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = "If and only if a watch face service defines wearableConfigurationAction metadata " +
                "with the value WATCH_FACE_EDITOR, there should be exactly one activity in the same package " +
                "which has an intent filter for WATCH_FACE_EDITOR (with $CATEGORY_NAME if minSdkVersion is less than 30).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_MANIFEST)

    override fun visitElement(context: XmlContext, element: Element) {
        val application = element.getElementsByTagName(TAG_APPLICATION).item(0) as? Element ?: return

        val minSdk = context.minSdk
        val requireCategory = minSdk < 30

        val services = mutableListOf<Element>()
        val activities = mutableListOf<Element>()

        for (i in 0 until application.childNodes.length) {
            val child = application.childNodes.item(i) as? Element ?: continue
            when (child.tagName) {
                TAG_SERVICE -> services.add(child)
                TAG_ACTIVITY -> activities.add(child)
            }
        }

        val targetServices = services.filter { service ->
            hasMetadata(service, METADATA_NAME, METADATA_VALUE)
        }

        if (targetServices.isEmpty()) return

        for (service in targetServices) {
            val serviceName = service.getAttribute(ATTR_NAME)
            val servicePackage = resolvePackage(element, serviceName)

            val matchingActivities = activities.filter { activity ->
                val activityName = activity.getAttribute(ATTR_NAME)
                val activityPackage = resolvePackage(element, activityName)
                if (activityPackage != servicePackage) return@filter false

                hasIntentFilter(activity, ACTION_NAME, if (requireCategory) CATEGORY_NAME else null)
            }

            if (matchingActivities.size != 1) {
                val message = if (matchingActivities.isEmpty()) {
                    "Missing watch face configuration activity for service $serviceName"
                } else {
                    "Duplicate watch face configuration activities found for service $serviceName"
                }
                context.report(ISSUE, service, context.getLocation(service), message)
            }
        }
    }

    private fun hasMetadata(service: Element, name: String, value: String): Boolean {
        for (i in 0 until service.childNodes.length) {
            val child = service.childNodes.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA &&
                child.getAttribute(ATTR_NAME) == name &&
                child.getAttribute(ATTR_VALUE) == value) {
                return true
            }
        }
        return false
    }

    private fun hasIntentFilter(activity: Element, action: String, category: String?): Boolean {
        for (i in 0 until activity.childNodes.length) {
            val child = activity.childNodes.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                var hasAction = false
                var hasCategory = category == null
                for (j in 0 until child.childNodes.length) {
                    val filterChild = child.childNodes.item(j) as? Element ?: continue
                    when (filterChild.tagName) {
                        TAG_ACTION -> if (filterChild.getAttribute(ATTR_NAME) == action) hasAction = true
                        TAG_CATEGORY -> if (filterChild.getAttribute(ATTR_NAME) == category) hasCategory = true
                    }
                }
                if (hasAction && hasCategory) return true
            }
        }
        return false
    }

    private fun resolvePackage(manifest: Element, componentName: String): String {
        if (componentName.isEmpty()) return ""
        val manifestPackage = manifest.getAttribute(ATTR_PACKAGE)
        return when {
            componentName.startsWith(".") -> "$manifestPackage$componentName"
            componentName.contains(".") -> componentName
            else -> "$manifestPackage.$componentName"
        }
    }
}
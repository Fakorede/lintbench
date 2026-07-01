package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ForegroundServicePermissionDetector : Detector(), Detector.XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element requires specific permissions to be declared in the manifest. Missing permissions will cause a SecurityException at runtime.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private val TYPE_REQUIRED_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA", "android.permission.CAMERA"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE", "android.permission.RECORD_AUDIO"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
        )
    }

    override fun getApplicableElements(): Collection<String> = listOf(SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion ?: context.project.targetSdkVersion ?: 0
        if (targetSdk < 34) return

        val fgsTypeAttr = element.getAttributeNS(ANDROID_URI, "foregroundServiceType")
        if (fgsTypeAttr.isNullOrEmpty() || fgsTypeAttr.startsWith("@")) return

        val declaredPermissions = getDeclaredPermissions(context)
        val types = fgsTypeAttr.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        for (type in types) {
            val required = TYPE_REQUIRED_PERMISSIONS[type] ?: continue
            val missing = mutableListOf<String>()

            if (type == "location") {
                if ("android.permission.FOREGROUND_SERVICE_LOCATION" !in declaredPermissions) {
                    missing.add("android.permission.FOREGROUND_SERVICE_LOCATION")
                }
                val hasLocationRuntime = "android.permission.ACCESS_FINE_LOCATION" in declaredPermissions ||
                                         "android.permission.ACCESS_COARSE_LOCATION" in declaredPermissions
                if (!hasLocationRuntime) {
                    missing.add("android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION")
                }
            } else {
                for (perm in required) {
                    if (perm !in declaredPermissions) {
                        missing.add(perm)
                    }
                }
            }

            if (missing.isNotEmpty()) {
                val message = "Foreground service type '$type' requires the following missing permissions: " +
                              "${missing.joinToString(", ")}. Declare them in the manifest to prevent a " +
                              "SecurityException on Android 14+."
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    message
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val root = context.document.documentElement ?: return permissions
        var child: Node? = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val el = child as Element
                if (el.tagName == "uses-permission") {
                    val name = el.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name.isNotEmpty()) {
                        permissions.add(name)
                    }
                }
            }
            child = child.nextSibling
        }
        return permissions
    }
}
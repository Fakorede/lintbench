package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import java.util.EnumSet
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = "For apps targeting Android 14 (API level 34) or higher, each foreground service " +
                    "type requires specific permissions to be declared in the manifest. If these permissions " +
                    "are missing, a SecurityException will be thrown when starting the service.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val typeToPermission = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "service") return

        val targetSdk = context.project.targetSdkVersion.featureLevel
        if (targetSdk < 34) return

        val foregroundServiceTypeAttr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType")
        if (foregroundServiceTypeAttr.isEmpty()) return

        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = element.ownerDocument.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val permissionItem = usesPermissions.item(i) as? Element ?: continue
            val name = permissionItem.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val usesPermissionsSdk23 = element.ownerDocument.getElementsByTagName("uses-permission-sdk-23")
        for (i in 0 until usesPermissionsSdk23.length) {
            val permissionItem = usesPermissionsSdk23.item(i) as? Element ?: continue
            val name = permissionItem.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val types = foregroundServiceTypeAttr.split('|').map { it.trim() }
        for (type in types) {
            val requiredPermission = typeToPermission[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Service declares `$type` foreground service type but does not declare the `$requiredPermission` permission in the manifest"
                )
            }
        }
    }
}
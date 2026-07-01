package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ForegroundServicePermissionDetector : ResourceXmlDetector(), XmlScanner, SourceCodeScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val project = context.project
        val targetSdk = project.targetSdkVersion.featureLevel
        if (targetSdk < 34) {
            return
        }

        val serviceTypeAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType") ?: return
        val serviceTypes = serviceTypeAttr.value.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (serviceTypes.isEmpty()) {
            return
        }

        val manifest = element.ownerDocument.documentElement ?: return
        val declaredPermissions = mutableSetOf<String>()
        var child = manifest.firstChild
        while (child != null) {
            if (child is org.w3c.dom.Element && child.tagName == "uses-permission") {
                val permissionName = child.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                if (permissionName.isNotEmpty()) {
                    declaredPermissions.add(permissionName)
                }
            }
            child = child.nextSibling
        }

        val typeToPermission = mapOf(
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

        for (type in serviceTypes) {
            val requiredPermission = typeToPermission[type]
            if (requiredPermission != null && !declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    serviceTypeAttr,
                    context.getLocation(serviceTypeAttr),
                    "Missing foreground service type permission: $requiredPermission is required for foregroundServiceType $type"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each foregroundServiceType listed in the <service> element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a foregroundServiceType that has \
                missing permissions, a SecurityException will be thrown.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ForegroundServicePermissionDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}
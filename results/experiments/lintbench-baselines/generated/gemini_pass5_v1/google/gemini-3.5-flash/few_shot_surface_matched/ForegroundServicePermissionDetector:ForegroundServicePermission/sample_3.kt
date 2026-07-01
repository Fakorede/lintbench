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

class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "service") return

        val targetSdk = context.project.targetSdkVersion
        if (targetSdk.featureLevel < 34) return

        val attributeNode = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType") ?: return
        val foregroundServiceTypeAttr = attributeNode.value ?: return
        if (foregroundServiceTypeAttr.isEmpty()) return

        val manifest = element.ownerDocument.documentElement ?: return
        val declaredPermissions = mutableSetOf<String>()
        var child = manifest.firstChild
        while (child != null) {
            if (child is org.w3c.dom.Element && (child.tagName == "uses-permission" || child.tagName == "uses-permission-sdk-23")) {
                val permissionName = child.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
                if (permissionName.isNotEmpty()) {
                    declaredPermissions.add(permissionName)
                }
            }
            child = child.nextSibling
        }

        val types = foregroundServiceTypeAttr.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        for (type in types) {
            val requiredPermission = when (type) {
                "camera" -> "android.permission.FOREGROUND_SERVICE_CAMERA"
                "connectedDevice" -> "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
                "dataSync" -> "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
                "health" -> "android.permission.FOREGROUND_SERVICE_HEALTH"
                "location" -> "android.permission.FOREGROUND_SERVICE_LOCATION"
                "mediaPlayback" -> "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
                "mediaProjection" -> "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
                "microphone" -> "android.permission.FOREGROUND_SERVICE_MICROPHONE"
                "phoneCall" -> "android.permission.FOREGROUND_SERVICE_PHONE_CALL"
                "remoteMessaging" -> "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"
                "specialUse" -> "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
                "systemExempted" -> "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
                else -> null
            }

            if (requiredPermission != null && !declaredPermissions.contains(requiredPermission)) {
                val message = "Service of type \"$type\" requires the \"$requiredPermission\" permission to be declared in the manifest."
                context.report(
                    ISSUE,
                    attributeNode,
                    context.getLocation(attributeNode),
                    message
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
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
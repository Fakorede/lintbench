package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element 
                requires specific sets of permissions to be declared in the manifest. If permissions are 
                missing, then when the foreground service is started with a `foregroundServiceType` that has 
                missing permissions, a `SecurityException` will be thrown.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private val REQUIRED_PERMISSIONS = mapOf(
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
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk in 1..33) {
            return
        }

        val serviceTypesString = element.getAttributeNS(ANDROID_URI, "foregroundServiceType")
        if (serviceTypesString.isNullOrEmpty()) {
            return
        }

        val serviceTypes = serviceTypesString.split('|').map { it.trim() }
        val declaredPermissions = getDeclaredPermissions(context)

        val attributeNode = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType")

        for (type in serviceTypes) {
            val requiredPermission = REQUIRED_PERMISSIONS[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE,
                    attributeNode ?: element,
                    context.getLocation(attributeNode ?: element),
                    "Missing permission `$requiredPermission` required for foreground service type `$type`"
                )
            }
        }
    }

    private fun getDeclaredPermissions(context: XmlContext): Set<String> {
        val permissions = mutableSetOf<String>()
        val document = context.document

        val usesPermissions = document.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }

        val usesPermissionsSdk23 = document.getElementsByTagName("uses-permission-sdk-23")
        for (i in 0 until usesPermissionsSdk23.length) {
            val element = usesPermissionsSdk23.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                permissions.add(name)
            }
        }

        return permissions
    }
}
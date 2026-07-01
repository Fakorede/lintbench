package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var targetSdkVersion = context.project.targetSdkVersion.featureLevel
        if (targetSdkVersion < 34) {
            val usesSdkList = element.ownerDocument.getElementsByTagName(SdkConstants.TAG_USES_SDK)
            if (usesSdkList.length > 0) {
                val usesSdk = usesSdkList.item(0) as Element
                val targetSdkStr = usesSdk.getAttributeNS(SdkConstants.ANDROID_URI, "targetSdkVersion")
                    .ifEmpty { usesSdk.getAttribute("android:targetSdkVersion") }
                val parsed = targetSdkStr.toIntOrNull()
                if (parsed != null) {
                    targetSdkVersion = parsed
                }
            }
        }

        if (targetSdkVersion < 34) {
            return
        }

        var serviceTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "foregroundServiceType")
        if (serviceTypeAttr == null) {
            serviceTypeAttr = element.getAttributeNode("android:foregroundServiceType")
        }
        if (serviceTypeAttr == null) {
            return
        }

        val serviceTypes = serviceTypeAttr.value.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (serviceTypes.isEmpty()) {
            return
        }

        val declaredPermissions = mutableSetOf<String>()
        val permissionTags = listOf(SdkConstants.TAG_USES_PERMISSION, "uses-permission-sdk-23")
        for (tag in permissionTags) {
            val usesPermissions = element.ownerDocument.getElementsByTagName(tag)
            for (i in 0 until usesPermissions.length) {
                val usesPermission = usesPermissions.item(i) as? Element ?: continue
                var name = usesPermission.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                if (name.isEmpty()) {
                    name = usesPermission.getAttribute("android:name")
                }
                if (name.isNotEmpty()) {
                    declaredPermissions.add(name)
                }
            }
        }

        val missingPermissions = mutableListOf<String>()
        for (type in serviceTypes) {
            val requiredPermission = typeToPermission[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                missingPermissions.add(requiredPermission)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val message = "The foreground service type(s) require the following permission(s) to be declared in the manifest: " +
                    missingPermissions.joinToString(", ")
            context.report(
                ISSUE_PERMISSION,
                serviceTypeAttr,
                context.getLocation(serviceTypeAttr),
                message
            )
        }
    }

    companion object {
        private val typeToPermission = mapOf(
            "camera" to "android.permission.FOREGROUND_SERVICE_CAMERA",
            "connectedDevice" to "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "dataSync" to "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "fileManagement" to "android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT",
            "health" to "android.permission.FOREGROUND_SERVICE_HEALTH",
            "location" to "android.permission.FOREGROUND_SERVICE_LOCATION",
            "mediaPlayback" to "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "mediaProjection" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "microphone" to "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "phoneCall" to "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "remoteMessaging" to "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING",
            "specialUse" to "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "mediaProcessing" to "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING"
        )

        val ISSUE_PERMISSION = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element 
                requires specific sets of permissions to be declared in the manifest. If permissions are 
                missing, then when the foreground service is started with a `foregroundServiceType` that has 
                missing permissions, a `SecurityException` will be thrown.
                """,
            category = Category.SECURITY,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        val ISSUE = ISSUE_PERMISSION
    }
}
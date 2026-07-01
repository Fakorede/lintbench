package com.android.tools.lint.checks

import com.android.SdkConstants
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion
        if (targetSdk.featureLevel < 34) {
            return
        }

        val serviceTypeAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "foregroundServiceType") ?: return
        val value = serviceTypeAttr.value ?: return
        if (value.isEmpty()) return

        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = element.ownerDocument.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val usesPermission = usesPermissions.item(i) as Element
            val name = usesPermission.getAttributeNS(SdkConstants.ANDROID_URI, "name")
            if (!name.isNullOrEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val types = value.split('|')
        for (type in types) {
            val trimmedType = type.trim()
            val requiredPermission = typeToPermission[trimmedType]
            if (requiredPermission != null && !declaredPermissions.contains(requiredPermission)) {
                context.report(
                    ISSUE_PERMISSION,
                    serviceTypeAttr,
                    context.getLocation(serviceTypeAttr),
                    "The <service> element specifies `foregroundServiceType=\"$trimmedType\"` but the manifest does not declare the `$requiredPermission` permission"
                )
            }
        }
    }

    companion object {
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
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "fileManagement" to "android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT"
        )

        @JvmField
        val ISSUE_PERMISSION = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
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

        @JvmField
        val ISSUE = ISSUE_PERMISSION
    }
}
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
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
        val ISSUE_PERMISSION = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
            """,
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

        private fun getRequiredPermission(type: String): String? {
            return when (type) {
                "camera" -> "android.permission.FOREGROUND_SERVICE_CAMERA"
                "connectedDevice" -> "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"
                "dataSync" -> "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
                "fileManagement" -> "android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT"
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
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val manifest = element.ownerDocument.documentElement ?: return

        var targetSdkVersion = context.project.targetSdkVersion.apiLevel
        if (targetSdkVersion < 34) {
            val usesSdk = manifest.getElementsByTagName("uses-sdk").item(0) as? Element
            if (usesSdk != null) {
                val targetSdkStr = usesSdk.getAttributeNS(ANDROID_URI, "targetSdkVersion")
                if (targetSdkStr.isNotEmpty()) {
                    val targetSdk = targetSdkStr.toIntOrNull()
                    if (targetSdk != null) {
                        targetSdkVersion = targetSdk
                    }
                }
            }
        }

        if (targetSdkVersion < 34) {
            return
        }

        val fgsTypeAttr = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: return
        val fgsTypeValue = fgsTypeAttr.value ?: return
        if (fgsTypeValue.isEmpty()) {
            return
        }

        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = manifest.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until usesPermissions.length) {
            val usesPermission = usesPermissions.item(i) as Element
            val name = usesPermission.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                val maxSdkVersionStr = usesPermission.getAttributeNS(ANDROID_URI, "maxSdkVersion")
                if (maxSdkVersionStr.isNotEmpty()) {
                    val maxSdk = maxSdkVersionStr.toIntOrNull()
                    if (maxSdk != null && maxSdk < 34) {
                        continue
                    }
                }
                declaredPermissions.add(name)
            }
        }

        val types = fgsTypeValue.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        for (type in types) {
            val requiredPermission = getRequiredPermission(type) ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                val location = context.getLocation(fgsTypeAttr)
                context.report(
                    ISSUE_PERMISSION,
                    fgsTypeAttr,
                    location,
                    "The foreground service type `$type` requires the `$requiredPermission` permission"
                )
            }
        }
    }
}
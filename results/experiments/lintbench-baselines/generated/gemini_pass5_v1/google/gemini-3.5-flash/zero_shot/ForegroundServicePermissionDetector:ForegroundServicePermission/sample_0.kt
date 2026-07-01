package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE
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
import org.w3c.dom.Document
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        val targetSdk = context.project.targetSdkVersion.featureLevel
        if (targetSdk in 1..33) {
            return
        }

        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = root.getElementsByTagName(TAG_USES_PERMISSION)
        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? Element ?: continue
            val name = item.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val services = root.getElementsByTagName(TAG_SERVICE)
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val foregroundServiceTypeAttr = service.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
            if (foregroundServiceTypeAttr.isNotEmpty()) {
                val types = foregroundServiceTypeAttr.split('|').map { it.trim() }
                for (type in types) {
                    val requiredPermission = getRequiredPermissionForType(type) ?: continue
                    if (!declaredPermissions.contains(requiredPermission)) {
                        val attrNode = service.getAttributeNodeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE)
                        val location = if (attrNode != null) context.getLocation(attrNode) else context.getLocation(service)
                        context.report(
                            ISSUE,
                            service,
                            location,
                            "The foreground service type `$type` requires the `$requiredPermission` permission in the manifest."
                        )
                    }
                }
            }
        }
    }

    private fun getRequiredPermissionForType(type: String): String? {
        return when (type) {
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
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing permissions required by foregroundServiceType",
            explanation = """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` \
                element requires specific sets of permissions to be declared in the manifest. \
                If permissions are missing, then when the foreground service is started with \
                a `foregroundServiceType` that has missing permissions, a `SecurityException` \
                will be thrown.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
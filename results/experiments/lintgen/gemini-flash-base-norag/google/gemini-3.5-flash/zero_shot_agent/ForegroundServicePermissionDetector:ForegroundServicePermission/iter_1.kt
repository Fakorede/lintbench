package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TAG_SERVICE
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 34) {
            return
        }

        var typeAttr = element.getAttributeNS(ANDROID_URI, "foregroundServiceType")
        if (typeAttr.isNullOrEmpty()) {
            typeAttr = element.getAttribute("android:foregroundServiceType")
        }
        if (typeAttr.isNullOrEmpty()) {
            return
        }

        val types = typeAttr.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (types.isEmpty()) {
            return
        }

        val document = element.ownerDocument ?: return
        val declaredPermissions = mutableSetOf<String>()
        val usesPermissions = document.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val item = usesPermissions.item(i) as? Element ?: continue
            var name = item.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isEmpty()) {
                name = item.getAttribute("android:name")
            }
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val missingPermissions = mutableListOf<String>()
        val missingTypes = mutableListOf<String>()

        for (type in types) {
            val requiredPermission = typeToPermission[type] ?: continue
            if (!declaredPermissions.contains(requiredPermission)) {
                missingPermissions.add(requiredPermission)
                missingTypes.add(type)
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val attributeNode = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType")
                ?: element.getAttributeNode("android:foregroundServiceType")
            val location = if (attributeNode != null) context.getLocation(attributeNode) else context.getLocation(element)
            val message = "The <service> element specifies `foregroundServiceType=\"${missingTypes.joinToString("|")}\"`, " +
                    "but the required permission `${missingPermissions.joinToString(", ")}` is not declared in the manifest."
            context.report(
                ISSUE_PERMISSION,
                element,
                location,
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
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

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
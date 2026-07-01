package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import java.util.EnumSet
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val TYPE_TO_PERMISSIONS = mapOf(
            "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
        )

        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

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
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("service")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdkVersion = context.project.targetSdkVersion
        if (targetSdkVersion.apiLevel < 34) {
            return
        }

        val typeAttr = element.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: return
        val typesValue = typeAttr.value ?: return
        val types = typesValue.split('|').map { it.trim() }.filter { it.isNotEmpty() }

        val document = element.ownerDocument ?: return
        val usesPermissions = document.getElementsByTagName("uses-permission")
        val declaredPermissions = mutableSetOf<String>()
        for (i in 0 until usesPermissions.length) {
            val permissionNode = usesPermissions.item(i) as? Element ?: continue
            val name = permissionNode.getAttributeNS(ANDROID_URI, "name")
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val missingPermissions = mutableListOf<String>()
        for (type in types) {
            val required = TYPE_TO_PERMISSIONS[type] ?: continue
            for (perm in required) {
                if (!declaredPermissions.contains(perm)) {
                    missingPermissions.add(perm)
                }
            }
        }

        if (missingPermissions.isNotEmpty()) {
            val message = "Missing permissions required by foregroundServiceType: " +
                    missingPermissions.joinToString(", ")
            context.report(
                ISSUE,
                typeAttr,
                context.getLocation(typeAttr),
                message
            )
        }
    }
}
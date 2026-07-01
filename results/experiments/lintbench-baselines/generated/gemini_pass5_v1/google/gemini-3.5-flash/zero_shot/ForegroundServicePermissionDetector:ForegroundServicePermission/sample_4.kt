package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        var targetSdkVersion = context.project.targetSdkVersion.featureLevel
        if (targetSdkVersion < 34) {
            val usesSdkNodes = element.getElementsByTagName("uses-sdk")
            if (usesSdkNodes.length > 0) {
                val usesSdk = usesSdkNodes.item(0) as Element
                val targetSdkStr = usesSdk.getAttributeNS("http://schemas.android.com/apk/res/android", "targetSdkVersion")
                if (targetSdkStr.isNotEmpty()) {
                    targetSdkVersion = targetSdkStr.toIntOrNull() ?: targetSdkVersion
                }
            }
        }

        if (targetSdkVersion < 34) {
            return
        }

        val usesPermissions = mutableSetOf<String>()
        val usesPermissionNodes = element.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissionNodes.length) {
            val permNode = usesPermissionNodes.item(i) as Element
            val name = permNode.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name.isNotEmpty()) {
                usesPermissions.add(name)
            }
        }

        val serviceNodes = element.getElementsByTagName("service")
        for (i in 0 until serviceNodes.length) {
            val serviceNode = serviceNodes.item(i) as Element
            val foregroundServiceType = serviceNode.getAttributeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType")
            if (foregroundServiceType.isNotEmpty()) {
                val types = foregroundServiceType.split('|').map { it.trim() }
                for (type in types) {
                    val requiredPermission = typeToPermission[type]
                    if (requiredPermission != null && !usesPermissions.contains(requiredPermission)) {
                        val attributeNode = serviceNode.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "foregroundServiceType")
                        val location = context.getLocation(attributeNode ?: serviceNode)
                        context.report(
                            ISSUE,
                            serviceNode,
                            location,
                            "The foreground service type `$type` requires the `$requiredPermission` permission"
                        )
                    }
                }
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
            "systemExempted" to "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"
        )

        @JvmField
        val ISSUE = Issue.create(
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
    }
}
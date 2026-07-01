package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import java.util.EnumSet
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ForegroundServicePermissionDetector::class.java,
            Scope.MANIFEST_SCOPE,
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
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "manifest") return

        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 34) return

        val declaredPermissions = mutableSetOf<String>()
        val services = mutableListOf<Element>()

        val manifestChildren = element.childNodes
        for (i in 0 until manifestChildren.length) {
            val child = manifestChildren.item(i)
            if (child is Element) {
                if (child.tagName == "uses-permission") {
                    val name = child.getAttributeNS(ANDROID_URI, "name")
                    if (name.isNotEmpty()) {
                        declaredPermissions.add(name)
                    }
                } else if (child.tagName == "application") {
                    val appChildren = child.childNodes
                    for (j in 0 until appChildren.length) {
                        val appChild = appChildren.item(j)
                        if (appChild is Element && appChild.tagName == "service") {
                            services.add(appChild)
                        }
                    }
                }
            }
        }

        for (service in services) {
            val fgsTypeAttr = service.getAttributeNS(ANDROID_URI, "foregroundServiceType")
            if (fgsTypeAttr.isNullOrEmpty()) {
                continue
            }
            val types = fgsTypeAttr.split('|').map { it.trim() }
            for (type in types) {
                val requiredPermission = getRequiredPermissionForType(type) ?: continue
                if (!declaredPermissions.contains(requiredPermission)) {
                    val location = context.getLocation(
                        service.getAttributeNodeNS(ANDROID_URI, "foregroundServiceType") ?: service
                    )
                    context.report(
                        ISSUE,
                        service,
                        location,
                        "Missing $requiredPermission which is required for foregroundServiceType $type"
                    )
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
}
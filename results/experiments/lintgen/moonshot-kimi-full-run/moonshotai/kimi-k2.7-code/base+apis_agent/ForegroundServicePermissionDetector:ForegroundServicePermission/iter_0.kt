package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SdkConstants
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ForegroundServicePermissionDetector : Detector(), XmlScanner {

    private var targetSdk: Int = -1
    private val permissions: MutableSet<String> = mutableSetOf()
    private val services: MutableList<ServiceInfo> = mutableListOf()

    override fun getApplicableElements(): Collection<String> = listOf(
        SdkConstants.TAG_USES_SDK,
        SdkConstants.TAG_USES_PERMISSION,
        SdkConstants.TAG_SERVICE
    )

    override fun beforeCheckFile(context: Context) {
        targetSdk = context.project.targetSdkVersion?.apiLevel ?: -1
        permissions.clear()
        services.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_USES_SDK -> {
                val target = element.getTargetSdk()
                if (target != null) {
                    targetSdk = target
                }
            }
            SdkConstants.TAG_USES_PERMISSION -> {
                val name = element.getAndroidName()
                if (name != null) {
                    permissions.add(name)
                }
            }
            SdkConstants.TAG_SERVICE -> {
                val attr = element.getForegroundServiceTypeAttr()
                if (attr != null) {
                    val types = attr.value
                        .split("|")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (types.isNotEmpty()) {
                        services.add(ServiceInfo(context.getLocation(attr), types))
                    }
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (targetSdk < TARGET_SDK_THRESHOLD) {
            return
        }

        for (service in services) {
            for (type in service.types) {
                val required = REQUIRED_PERMISSIONS[type] ?: continue
                val missing = required.filter { it !in permissions }
                if (missing.isNotEmpty()) {
                    val message = "Missing permission(s) required for " +
                        "foregroundServiceType '$type': ${missing.joinToString()}"
                    context.report(ISSUE, service.location, message)
                }
            }
        }
    }

    private fun Element.getTargetSdk(): Int? {
        val value = getAndroidAttribute(SdkConstants.ATTR_TARGET_SDK_VERSION)
            ?: getAttribute(SdkConstants.ATTR_TARGET_SDK_VERSION)
        return value.toIntOrNull()
    }

    private fun Element.getAndroidName(): String? {
        return getAndroidAttribute(SdkConstants.ATTR_NAME)
            ?: getAttribute(SdkConstants.ATTR_NAME)
    }

    private fun Element.getForegroundServiceTypeAttr(): Attr? {
        return getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE)
            ?: getAttributeNode(SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE)
    }

    private fun Element.getAndroidAttribute(name: String): String? {
        val value = getAttributeNS(SdkConstants.ANDROID_URI, name)
        return if (value.isNotEmpty()) value else null
    }

    private data class ServiceInfo(
        val location: Location,
        val types: List<String>
    )

    companion object {
        private const val TARGET_SDK_THRESHOLD = 34

        private val REQUIRED_PERMISSIONS = mapOf(
            "dataSync" to listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            "mediaPlayback" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            "phoneCall" to listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            "mediaProjection" to listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            "camera" to listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
            "microphone" to listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            "location" to listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
            "connectedDevice" to listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "health" to listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            "remoteMessaging" to listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            "specialUse" to listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            "systemExempted" to listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
        )

        val ISSUE = Issue.create(
            id = "ForegroundServicePermission",
            briefDescription = "Missing foreground service permission",
            explanation = """
                For apps targeting Android 14 (API 34) and higher, each
                `android:foregroundServiceType` declared on a `<service>` element
                requires a corresponding `FOREGROUND_SERVICE_*` permission to be
                declared in the manifest. If the required permission is missing, the
                system throws a `SecurityException` when the service is started as a
                foreground service with that type.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ForegroundServicePermissionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
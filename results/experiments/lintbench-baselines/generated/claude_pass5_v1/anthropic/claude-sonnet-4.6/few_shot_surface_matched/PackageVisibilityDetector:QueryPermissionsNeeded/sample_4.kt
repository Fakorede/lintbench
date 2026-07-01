package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    // -----------------------------------------------------------------------
    // XmlScanner – detect ContentResolver / launcher-type service declarations
    // that imply the app interacts with other packages.
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_SERVICE)

    override fun visitElement(context: XmlContext, element: Element) {
        // We only care about services that declare an intent-filter with one of
        // the known actions that require package-visibility queries.
        var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
        while (intentFilter != null) {
            var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
            while (action != null) {
                val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (actionName in QUERY_INTENT_ACTIONS) {
                    val attrNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                    val location =
                        if (attrNode != null) context.getValueLocation(attrNode)
                        else context.getLocation(element)
                    val incident = Incident(
                        ISSUE,
                        element,
                        location,
                        "Service with action `$actionName` may need a `<queries>` declaration " +
                            "in the manifest to be visible to other apps on Android 11+",
                    )
                    context.report(incident, targetSdkAtLeast(TARGET_API))
                    return
                }
                action = getNextTagByName(action, TAG_ACTION)
            }
            intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – detect calls to PackageManager APIs that are
    // restricted on Android 11+.
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = RESTRICTED_METHODS.keys.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) return

        val description = RESTRICTED_METHODS[methodName] ?: return

        val message =
            "`PackageManager#$methodName` $description. " +
                "Consider adding a `<queries>` declaration to your manifest. " +
                "See https://g.co/dev/packagevisibility for details."

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        context.report(incident, targetSdkAtLeast(TARGET_API))
    }

    // -----------------------------------------------------------------------
    // filterIncident – store extra data for conditional reporting
    // -----------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // We rely on targetSdkAtLeast constraint passed via report(); nothing
        // extra to store, so just allow the incident to proceed.
        return true
    }

    // -----------------------------------------------------------------------
    // Companion
    // -----------------------------------------------------------------------

    companion object {
        private const val TARGET_API = 30 // Android 11 = API 30
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        /** Methods whose unrestricted use is affected by package-visibility filtering. */
        private val RESTRICTED_METHODS: Map<String, String> = mapOf(
            "getInstalledPackages" to
                "will no longer return information about all installed apps on Android 11+",
            "getInstalledApplications" to
                "will no longer return information about all installed apps on Android 11+",
            "queryBroadcastReceivers" to
                "results may be incomplete on Android 11+ without a `<queries>` element",
            "queryContentProviders" to
                "results may be incomplete on Android 11+ without a `<queries>` element",
            "queryIntentServices" to
                "results may be incomplete on Android 11+ without a `<queries>` element",
            "queryIntentActivities" to
                "results may be incomplete on Android 11+ without a `<queries>` element",
            "resolveActivity" to
                "may return null on Android 11+ if the target package is not visible",
            "resolveService" to
                "may return null on Android 11+ if the target package is not visible",
            "getPackageInfo" to
                "will throw `NameNotFoundException` on Android 11+ if the target package is not visible",
            "getApplicationInfo" to
                "will throw `NameNotFoundException` on Android 11+ if the target package is not visible",
        )

        /** Intent actions that indicate cross-package interaction. */
        private val QUERY_INTENT_ACTIONS: Set<String> = setOf(
            "android.intent.action.SEND",
            "android.intent.action.SENDTO",
            "android.intent.action.SEND_MULTIPLE",
            "android.intent.action.VIEW",
            "android.intent.action.PICK",
            "android.media.browse.MediaBrowserService",
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation =
                """
                Apps that target Android 11 cannot query or interact with other installed \
                apps by default. If you need to query or interact with other installed apps, \
                you may need to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information \
                about all installed apps. To query specific apps or types of apps, you can \
                use methods like `PackageManager#getPackageInfo` or \
                `PackageManager#queryIntentActivities`.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            moreInfo = "https://g.co/dev/packagevisibility",
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                Scope.JAVA_FILE_SCOPE,
                Scope.MANIFEST_SCOPE,
            ),
        )
    }
}
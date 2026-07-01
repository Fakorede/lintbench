package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"
        private const val KEY_LOCATION = "location"

        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"

        private const val ASSET_STATEMENTS_META_DATA =
            "asset_statements"

        @JvmField
        val ISSUE =
            Issue.create(
                id = "CredManMissingDal",
                briefDescription = "Missing Digital Asset Link for Credential Manager",
                explanation =
                    """
                    When using password sign-in through Credential Manager, an asset statements \
                    string resource file that includes the `assetlinks.json` files to load must \
                    be declared in the manifest using a `<meta-data>` element.

                    See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                    for details.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        CredentialManagerDigitalAssetLinkDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    ),
                androidSpecific = true,
            )
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf(PASSWORD_CREDENTIAL_CLASS, GET_PASSWORD_OPTION_CLASS)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        val location = context.getLocation(node)
        val map = context.getPartialResults(ISSUE).map()
        map.put(KEY_USES_PASSWORD_CREDENTIAL, true)
        // Store the location of the first usage found so we can report it later
        if (!map.containsKey(KEY_LOCATION)) {
            map.put(KEY_LOCATION, location)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Collect whether any module uses password credentials
        var usesPasswordCredential = false
        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) == true) {
                usesPasswordCredential = true
                break
            }
        }

        if (!usesPasswordCredential) return

        // Check if the manifest declares the asset_statements meta-data
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val applicationNode =
            mergedManifest.documentElement
                ?.let { root ->
                    var child = root.firstChild
                    while (child != null) {
                        if (child.nodeName == SdkConstants.TAG_APPLICATION) break
                        child = child.nextSibling
                    }
                    child
                }
                ?: return

        var hasAssetStatements = false
        var child = applicationNode.firstChild
        while (child != null) {
            if (child.nodeName == SdkConstants.TAG_META_DATA) {
                val nameAttr =
                    (child as? org.w3c.dom.Element)
                        ?.getAttributeNS(
                            SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_NAME,
                        )
                if (nameAttr != null && nameAttr.contains(ASSET_STATEMENTS_META_DATA)) {
                    hasAssetStatements = true
                    break
                }
            }
            child = child.nextSibling
        }

        if (!hasAssetStatements) {
            // Find the first stored location to report against
            var location: com.android.tools.lint.detector.api.Location? = null
            for ((_, map) in partialResults) {
                val loc = map.getLocation(KEY_LOCATION)
                if (loc != null) {
                    location = loc
                    break
                }
            }

            val message =
                "When using Credential Manager with password sign-in, you must declare " +
                    "an `asset_statements` `<meta-data>` element in your manifest pointing " +
                    "to your Digital Asset Links JSON file. " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"

            if (location != null) {
                context.report(Incident(ISSUE, location, message))
            } else {
                context.report(
                    Incident(
                        ISSUE,
                        com.android.tools.lint.detector.api.Location.create(context.mainProject.dir),
                        message,
                    )
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // For single-module projects (non-partial analysis), handle here
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }
}
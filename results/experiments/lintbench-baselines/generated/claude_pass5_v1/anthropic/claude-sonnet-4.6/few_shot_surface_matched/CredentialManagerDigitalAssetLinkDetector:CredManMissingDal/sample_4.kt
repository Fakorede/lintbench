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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"
        private const val KEY_HAS_DAL_META_DATA = "hasDalMetaData"

        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"

        private const val META_DATA_NAME =
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
                        Scope.JAVA_FILE_SCOPE,
                    ),
                androidSpecific = true,
            )
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableConstructorTypes(): List<String> =
        listOf(PASSWORD_CREDENTIAL_CLASS, GET_PASSWORD_OPTION_CLASS)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Record that the project uses password-based Credential Manager APIs.
        context.getPartialResults(ISSUE).map().put(KEY_USES_PASSWORD_CREDENTIAL, true)
    }

    // -------------------------------------------------------------------------
    // Partial / incremental results
    // -------------------------------------------------------------------------

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Merge flags from all modules into the root project's map so that
        // afterCheckRootProject can make the final decision.
        var usesPassword = false
        var hasDal = false

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) == true) usesPassword = true
            if (map.getBoolean(KEY_HAS_DAL_META_DATA) == true) hasDal = true
        }

        val rootMap = context.getPartialResults(ISSUE).map()
        if (usesPassword) rootMap.put(KEY_USES_PASSWORD_CREDENTIAL, true)
        if (hasDal) rootMap.put(KEY_HAS_DAL_META_DATA, true)
    }

    // -------------------------------------------------------------------------
    // afterCheckRootProject – check the Android manifest for the meta-data tag
    // and emit the issue if needed.
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        val partialResults = context.getPartialResults(ISSUE)
        val map: LintMap = partialResults.map()

        val usesPassword = map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) ?: false
        if (!usesPassword) return

        // Check whether any merged manifest contains the required <meta-data>.
        val hasDal = map.getBoolean(KEY_HAS_DAL_META_DATA) ?: false
        if (hasDal) return

        // Also check the manifest of the root project directly (non-partial path).
        if (hasAssetStatementsMetaData(context)) return

        val message =
            "When using Credential Manager password sign-in, you must declare an " +
                "`asset_statements` `<meta-data>` element in your AndroidManifest.xml " +
                "pointing to your Digital Asset Links JSON file. " +
                "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"

        context.report(
            Incident(
                ISSUE,
                message,
                // Use the project-level location (no specific source node).
                context.getLocation(context.project.manifestFiles.firstOrNull()
                    ?: return),
            )
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the manifest file(s) of the given context's project and looks for
     * a `<meta-data android:name="asset_statements" …>` element inside
     * `<application>`.
     */
    private fun hasAssetStatementsMetaData(context: Context): Boolean {
        val project = context.project
        for (manifestFile in project.manifestFiles) {
            try {
                val document =
                    context.client.getMergedManifest(project) ?: continue
                val appNodes =
                    document.getElementsByTagName(SdkConstants.TAG_APPLICATION)
                for (i in 0 until appNodes.length) {
                    val appNode = appNodes.item(i)
                    val children = appNode.childNodes
                    for (j in 0 until children.length) {
                        val child = children.item(j)
                        if (child.nodeName == SdkConstants.TAG_META_DATA) {
                            val nameAttr =
                                child.attributes?.getNamedItemNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_NAME,
                                )
                                    ?: child.attributes?.getNamedItem(
                                        "${SdkConstants.ANDROID_NS_NAME}:${SdkConstants.ATTR_NAME}"
                                    )
                            if (nameAttr != null &&
                                nameAttr.nodeValue.contains(META_DATA_NAME, ignoreCase = true)
                            ) {
                                return true
                            }
                        }
                    }
                }
            } catch (ignore: Exception) {
                // If we can't parse the manifest, skip it.
            }
        }
        return false
    }
}
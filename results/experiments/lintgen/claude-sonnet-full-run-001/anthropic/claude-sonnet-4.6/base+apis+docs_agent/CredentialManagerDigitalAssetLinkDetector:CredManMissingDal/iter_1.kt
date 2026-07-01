package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST_CLASS =
            "androidx.credentials.CreatePasswordRequest"

        private const val META_DATA_ASSET_STATEMENTS =
            "asset_statements"
        private const val META_DATA_ASSET_STATEMENTS_FULL =
            "androidx.credentials.ASSET_STATEMENTS"

        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside `<application>` in your AndroidManifest.xml \
                that points to a string resource containing the Digital Asset Links JSON. \
                For example:

                ```xml
                <meta-data
                    android:name="asset_statements"
                    android:value="@string/asset_statements" />
                ```

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.JAVA_FILE)
            ),
            moreInfo = "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    // Whether the project uses Credential Manager password APIs
    private var usesCredentialManagerPasswordApi = false

    // Whether the manifest has the required asset_statements meta-data
    private var hasAssetStatementsMetaData = false

    // Location to report the issue (application element in manifest)
    private var applicationElementLocation: Location? = null

    // Location of the credential manager usage in source
    private var credentialManagerUsageLocation: Location? = null

    // -----------------------------------------------------------------------
    // XmlScanner – manifest inspection
    // -----------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_APPLICATION, TAG_META_DATA)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                if (applicationElementLocation == null) {
                    applicationElementLocation = context.getLocation(element)
                }
            }
            TAG_META_DATA -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
                if (name == META_DATA_ASSET_STATEMENTS ||
                    name == META_DATA_ASSET_STATEMENTS_FULL ||
                    name.contains("asset_statements", ignoreCase = true)
                ) {
                    hasAssetStatementsMetaData = true
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – Java/Kotlin source inspection
    // -----------------------------------------------------------------------

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            PASSWORD_CREDENTIAL_CLASS,
            GET_PASSWORD_OPTION_CLASS,
            CREATE_PASSWORD_REQUEST_CLASS
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!usesCredentialManagerPasswordApi) {
            usesCredentialManagerPasswordApi = true
            credentialManagerUsageLocation = context.getLocation(node)
        }
    }

    // -----------------------------------------------------------------------
    // Project-level hooks – report after all files have been analyzed
    // -----------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        checkAndReport(context)
    }

    override fun afterCheckEachProject(context: Context) {
        checkAndReport(context)
    }

    private fun checkAndReport(context: Context) {
        if (usesCredentialManagerPasswordApi && !hasAssetStatementsMetaData) {
            val location = applicationElementLocation
                ?: credentialManagerUsageLocation
                ?: Location.create(context.project.dir)

            context.report(
                issue = ISSUE,
                location = location,
                message = "Missing `asset_statements` `<meta-data>` in the manifest. " +
                    "When using Credential Manager password sign-in, you must declare a " +
                    "`<meta-data android:name=\"asset_statements\" .../>` element inside " +
                    "`<application>` pointing to your Digital Asset Links JSON. " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
            )
        }
        // Reset state for next project
        usesCredentialManagerPasswordApi = false
        hasAssetStatementsMetaData = false
        applicationElementLocation = null
        credentialManagerUsageLocation = null
    }
}
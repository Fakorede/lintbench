package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
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

/**
 * Detector that checks whether an application using Credential Manager
 * for password sign-in has declared the required Digital Asset Link
 * `<meta-data>` element in the AndroidManifest.xml.
 *
 * Reference:
 *   https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
 */
class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    // -------------------------------------------------------------------------
    // State shared between the XML pass and the source pass
    // -------------------------------------------------------------------------

    /** Whether the manifest already contains the required <meta-data> element. */
    private var dalMetaDataPresent = false

    /**
     * Locations where Credential Manager password-related APIs are used,
     * collected during the source-code pass.
     */
    private val credentialManagerUsages = mutableListOf<Location>()

    // -------------------------------------------------------------------------
    // XmlScanner – inspect AndroidManifest.xml
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> = listOf(TAG_META_DATA)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode?.nodeName != TAG_APPLICATION) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (name == ASSET_STATEMENTS_META_DATA_NAME) {
            // Verify that a value / resource reference is actually provided.
            val value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE)
            if (value.isNotBlank()) {
                dalMetaDataPresent = true
            }
        }
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner – detect Credential Manager password API usage
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        // PasswordCredential constructor
        "PasswordCredential",
        // GetPasswordOption constructor / factory
        "GetPasswordOption",
        // CredentialManager.getCredential / getCredentialAsync
        "getCredential",
        "getCredentialAsync",
        // CredentialManager.createCredential / createCredentialAsync
        "createCredential",
        "createCredentialAsync",
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isRelevant = when {
            containingClass == PASSWORD_CREDENTIAL_CLASS -> true
            containingClass == GET_PASSWORD_OPTION_CLASS -> true
            containingClass in CREDENTIAL_MANAGER_CLASSES -> true
            else -> false
        }

        if (isRelevant) {
            credentialManagerUsages.add(context.getLocation(node))
        }
    }

    // -------------------------------------------------------------------------
    // afterCheckProject – report if needed
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        if (dalMetaDataPresent) return          // everything is fine
        if (credentialManagerUsages.isEmpty()) return  // API not used

        // Report on the first usage site; secondary locations point to others.
        val primary = credentialManagerUsages.first()
        val secondaryLocations = credentialManagerUsages.drop(1).fold(null as Location?) { acc, loc ->
            loc.withSecondary(acc, "Also used here")
        }

        context.report(
            issue = ISSUE,
            location = primary.withSecondary(secondaryLocations, "Also used here"),
            message = "Missing Digital Asset Link declaration for Credential Manager. " +
                "Add a `<meta-data>` element with " +
                "`android:name=\"$ASSET_STATEMENTS_META_DATA_NAME\"` and " +
                "`android:value=\"@string/<asset_statements_resource>\"` inside `<application>` " +
                "in your AndroidManifest.xml. " +
                "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal"
        )
    }

    // -------------------------------------------------------------------------
    // Companion – issue definition and constants
    // -------------------------------------------------------------------------

    companion object {

        private const val ASSET_STATEMENTS_META_DATA_NAME =
            "asset_statements"

        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"

        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"

        private val CREDENTIAL_MANAGER_CLASSES = setOf(
            "androidx.credentials.CredentialManager",
            "androidx.credentials.CredentialManagerImpl"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must \
                be declared in the manifest using a `<meta-data>` element.

                Add the following inside the `<application>` tag of your \
                `AndroidManifest.xml`:
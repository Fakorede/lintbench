package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_RESOURCE
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
 * Detector that checks whether apps using Credential Manager for password sign-in
 * have properly declared a Digital Asset Link (DAL) meta-data element in their manifest.
 *
 * See: https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
 */
class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private const val ASSET_STATEMENTS_META_DATA_NAME =
            "asset_statements"

        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"

        private const val GET_CREDENTIAL_METHOD = "getCredential"
        private const val GET_CREDENTIAL_ASYNC_METHOD = "getCredentialAsync"

        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"

        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"

        private const val CREATE_PASSWORD_REQUEST_CLASS =
            "androidx.credentials.CreatePasswordRequest"

        private val CREDENTIAL_MANAGER_METHODS = setOf(
            "getCredential",
            "getCredentialAsync",
            "createCredential",
            "createCredentialAsync"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add a `<meta-data>` element inside the `<application>` tag in your \
                `AndroidManifest.xml` with the name `asset_statements` pointing to a string \
                resource that lists the Digital Asset Link JSON files:
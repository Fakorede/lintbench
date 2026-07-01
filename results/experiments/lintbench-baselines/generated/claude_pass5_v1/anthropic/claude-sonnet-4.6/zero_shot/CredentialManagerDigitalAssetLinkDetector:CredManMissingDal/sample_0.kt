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

class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner, SourceCodeScanner {

    // Track whether Credential Manager password sign-in is used
    private var usesPasswordCredential = false
    private var usesGetCredentialRequest = false
    private var credentialManagerCallLocation: Location? = null

    // Track whether the DAL meta-data is declared in the manifest
    private var hasDalMetaData = false

    // Track manifest location for reporting
    private var manifestApplicationLocation: Location? = null

    companion object {
        private const val ASSET_STATEMENTS_META_DATA_NAME =
            "asset_statements"
        private const val ANDROID_ASSET_STATEMENTS =
            "android.app.lib_name"

        // Known meta-data names for asset statements
        private val ASSET_STATEMENTS_NAMES = setOf(
            "asset_statements",
            "com.google.android.gms.wallet.api.enabled"
        )

        private const val GET_CREDENTIAL_REQUEST_CLASS =
            "androidx.credentials.GetCredentialRequest"
        private const val GET_PASSWORD_OPTION_CLASS =
            "androidx.credentials.GetPasswordOption"
        private const val PASSWORD_CREDENTIAL_CLASS =
            "androidx.credentials.PasswordCredential"
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                Add an `asset_statements` string resource and declare it in your \
                `AndroidManifest.xml` inside the `<application>` tag:
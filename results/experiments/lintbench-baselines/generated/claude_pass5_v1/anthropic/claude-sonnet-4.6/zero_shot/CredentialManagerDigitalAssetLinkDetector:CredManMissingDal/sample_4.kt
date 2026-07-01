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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

/**
 * Detector that checks whether apps using Credential Manager for password sign-in
 * have declared the required Digital Asset Links meta-data in their AndroidManifest.xml.
 *
 * Reference: https://developer.android.com/identity/sign-in/credential-manager#add-support-dal
 */
class CredentialManagerDigitalAssetLinkDetector : Detector(), XmlScanner {

    companion object {
        private const val ASSET_STATEMENTS_META_DATA_NAME =
            "asset_statements"

        private const val CREDENTIALS_META_DATA_NAME =
            "credentials"

        // The fully-qualified meta-data name used by Credential Manager for DAL
        private const val DAL_META_DATA_NAME =
            "androidx.credentials.TYPE_PASSWORD_SUPPORTED"

        // Alternative / legacy meta-data names that satisfy the DAL requirement
        private val ACCEPTED_META_DATA_NAMES = setOf(
            DAL_META_DATA_NAME,
            "asset_statements",
            "credentials"
        )

        // Gradle / library dependency coordinates that indicate Credential Manager usage
        private const val CREDENTIAL_MANAGER_ARTIFACT = "androidx.credentials:credentials"

        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation =
                """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element inside `<application>`.

                Add a `<meta-data>` element to your `<application>` block similar to:
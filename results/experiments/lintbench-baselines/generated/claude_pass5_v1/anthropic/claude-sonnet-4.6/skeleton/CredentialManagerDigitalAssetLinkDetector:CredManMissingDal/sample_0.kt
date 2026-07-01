package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CredentialManagerDigitalAssetLinkDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements \
                string resource file that includes the `assetlinks.json` files to load must be \
                declared in the manifest using a `<meta-data>` element.

                See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal \
                for details on how to add the Digital Asset Links support.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // The class name for PasswordCredential
        private const val PASSWORD_CREDENTIAL_CLASS = "androidx.credentials.PasswordCredential"

        // The class name for GetPasswordOption
        private const val GET_PASSWORD_OPTION_CLASS = "androidx.credentials.GetPasswordOption"

        // The meta-data name that must be present in the manifest
        private const val ASSET_STATEMENTS_META_DATA = "asset_statements"

        // Key used to store findings in partial results map
        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"

        // Key used to store the location information
        private const val KEY_LOCATION = "location"
    }

    /**
     * Track whether we've found usage of password credential APIs
     * and whether the manifest has the required meta-data.
     */
    private var usesPasswordCredential = false
    private var credentialLocation: com.android.tools.lint.detector.api.Location? = null

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        PASSWORD_CREDENTIAL_CLASS,
        GET_PASSWORD_OPTION_CLASS,
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Record that password credential APIs are being used
        usesPasswordCredential = true
        if (credentialLocation == null) {
            credentialLocation = context.getLocation(node)
        }

        // Store in partial results for multi-module analysis
        val map = context.getPartialResults(ISSUE).map()
        map.put(KEY_USES_PASSWORD_CREDENTIAL, true)
        map.put(KEY_LOCATION, context.getLocation(node))
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Check if any module uses password credentials
        var foundPasswordCredential = false
        var location: com.android.tools.lint.detector.api.Location? = null

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) == true) {
                foundPasswordCredential = true
                if (location == null) {
                    location = map.getLocation(KEY_LOCATION)
                }
            }
        }

        if (!foundPasswordCredential) {
            return
        }

        // Check manifest for the required meta-data
        if (!hasAssetStatementsMetaData(context)) {
            val reportLocation = location ?: context.project.dir.let {
                com.android.tools.lint.detector.api.Location.create(it)
            }
            context.report(
                ISSUE,
                reportLocation,
                "When using Credential Manager `PasswordCredential`, you must include an " +
                    "`asset_statements` `<meta-data>` element in your manifest pointing to " +
                    "a string resource that lists the Digital Asset Links JSON files. " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!usesPasswordCredential) {
            return
        }

        // Check manifest for the required meta-data
        if (!hasAssetStatementsMetaData(context)) {
            val reportLocation = credentialLocation
                ?: com.android.tools.lint.detector.api.Location.create(context.project.dir)
            context.report(
                ISSUE,
                reportLocation,
                "When using Credential Manager `PasswordCredential`, you must include an " +
                    "`asset_statements` `<meta-data>` element in your manifest pointing to " +
                    "a string resource that lists the Digital Asset Links JSON files. " +
                    "See https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            )
        }
    }

    /**
     * Checks whether the project's merged manifest contains the required
     * `asset_statements` meta-data element in the application element.
     */
    private fun hasAssetStatementsMetaData(context: Context): Boolean {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return false
        val documentElement = mergedManifest.documentElement ?: return false

        // Look for <application> element
        val applicationNodes = documentElement.getElementsByTagName("application")
        for (i in 0 until applicationNodes.length) {
            val applicationNode = applicationNodes.item(i) ?: continue
            val childNodes = applicationNode.childNodes
            for (j in 0 until childNodes.length) {
                val child = childNodes.item(j) ?: continue
                if (child.nodeName == "meta-data") {
                    val nameAttr = child.attributes?.getNamedItem("android:name")?.nodeValue
                    if (nameAttr != null && nameAttr.contains(ASSET_STATEMENTS_META_DATA)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
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
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Credential Manager password-related classes
        private val PASSWORD_CREDENTIAL_CLASSES = listOf(
            "androidx.credentials.PasswordCredential",
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest",
        )

        // The meta-data name that must be present in the manifest for DAL support
        private const val ASSET_STATEMENTS_META_DATA =
            "asset_statements"

        // Key used in LintMap to track whether password credential usage was found
        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"

        // Key used in LintMap to store the location of the first usage
        private const val KEY_LOCATION = "location"
    }

    override fun getApplicableConstructorTypes(): List<String> = PASSWORD_CREDENTIAL_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Record that we found a usage of password credential APIs.
        // We store a flag in the partial results map so that checkPartialResults / afterCheckRootProject
        // can later verify whether the manifest has the required meta-data.
        val map = context.getPartialResults(ISSUE).map()
        if (!map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)) {
            map.put(KEY_USES_PASSWORD_CREDENTIAL, true)
            // Store the location of the first usage so we can report it later
            val location = context.getLocation(node)
            map.put(KEY_LOCATION, location)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // In multi-module / partial-analysis builds this is called to merge results.
        // Check whether any module recorded password-credential usage, and if so verify
        // that the manifest contains the required asset_statements meta-data.
        var usesPasswordCredential = false
        var firstLocation: com.android.tools.lint.detector.api.Location? = null

        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)) {
                usesPasswordCredential = true
                val loc = map.getLocation(KEY_LOCATION)
                if (firstLocation == null && loc != null) {
                    firstLocation = loc
                }
            }
        }

        if (usesPasswordCredential) {
            if (!manifestHasAssetStatementsMetaData(context)) {
                val location = firstLocation ?: context.project.manifestFiles
                    .firstOrNull()
                    ?.let { com.android.tools.lint.detector.api.Location.create(it) }
                if (location != null) {
                    context.report(
                        issue = ISSUE,
                        location = location,
                        message = "This app uses Credential Manager password sign-in but does not " +
                            "declare the required `asset_statements` `<meta-data>` element in the " +
                            "manifest. See https://developer.android.com/identity/sign-in/" +
                            "credential-manager#add-support-dal",
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // In non-partial-analysis (single-module) builds, checkPartialResults is not called,
        // so we handle the check here.
        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map()

        val usesPasswordCredential = map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL, false)
        if (usesPasswordCredential) {
            if (!manifestHasAssetStatementsMetaData(context)) {
                val location = map.getLocation(KEY_LOCATION)
                    ?: context.project.manifestFiles
                        .firstOrNull()
                        ?.let { com.android.tools.lint.detector.api.Location.create(it) }
                if (location != null) {
                    context.report(
                        issue = ISSUE,
                        location = location,
                        message = "This app uses Credential Manager password sign-in but does not " +
                            "declare the required `asset_statements` `<meta-data>` element in the " +
                            "manifest. See https://developer.android.com/identity/sign-in/" +
                            "credential-manager#add-support-dal",
                    )
                }
            }
        }
    }

    /**
     * Checks whether any of the project's merged manifests contain a `<meta-data>` element
     * whose `android:name` attribute contains "asset_statements".
     */
    private fun manifestHasAssetStatementsMetaData(context: Context): Boolean {
        val project = context.project

        // Check all manifest files available to this project
        for (manifestFile in project.manifestFiles) {
            try {
                val content = manifestFile.readText()
                // Look for a meta-data element referencing asset_statements
                if (content.contains(ASSET_STATEMENTS_META_DATA, ignoreCase = true)) {
                    return true
                }
            } catch (e: Exception) {
                // If we can't read the manifest, skip it
            }
        }

        // Also check merged manifest if available
        val mergedManifest = project.mergedManifest
        if (mergedManifest != null) {
            try {
                val root = mergedManifest.documentElement
                if (root != null && elementContainsAssetStatements(root)) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return false
    }

    /**
     * Recursively checks whether the given XML element or any of its descendants is a
     * `<meta-data>` element whose `android:name` or `name` attribute contains "asset_statements".
     */
    private fun elementContainsAssetStatements(element: org.w3c.dom.Element): Boolean {
        if (element.tagName == "meta-data") {
            val name = element.getAttribute("android:name")
                .ifEmpty { element.getAttribute("name") }
            if (name.contains(ASSET_STATEMENTS_META_DATA, ignoreCase = true)) {
                return true
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element) {
                if (elementContainsAssetStatements(child)) {
                    return true
                }
            }
        }

        return false
    }
}
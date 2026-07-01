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
import org.w3c.dom.Element

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
                for details on how to add Digital Asset Link support.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Credential Manager password credential classes
        private const val PASSWORD_CREDENTIAL_OPTION =
            "androidx.credentials.GetPasswordOption"
        private const val CREATE_PASSWORD_REQUEST =
            "androidx.credentials.CreatePasswordRequest"

        // Meta-data name for asset statements
        private const val ASSET_STATEMENTS_META_DATA =
            "asset_statements"

        // Key used in LintMap to record that password credentials are used
        private const val KEY_USES_PASSWORD_CREDENTIALS = "usesPasswordCredentials"

        // Key used in LintMap to record call location for reporting
        private const val KEY_LOCATION = "location"
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        PASSWORD_CREDENTIAL_OPTION,
        CREATE_PASSWORD_REQUEST,
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Record that the project uses password credentials
        val map = context.getPartialResults(ISSUE).map()
        map.put(KEY_USES_PASSWORD_CREDENTIALS, true)
        // Store the location of the first usage for reporting purposes
        if (!map.containsKey(KEY_LOCATION)) {
            map.put(KEY_LOCATION, context.getLocation(node))
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Check if any module uses password credentials
        var usesPasswordCredentials = false
        for (map in partialResults.maps()) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIALS) == true) {
                usesPasswordCredentials = true
                break
            }
        }

        if (!usesPasswordCredentials) {
            return
        }

        // Check the merged manifest for the asset_statements meta-data
        if (!hasAssetStatementsMetaData(context)) {
            // Find the location to report - use the first recorded location if available
            var location: com.android.tools.lint.detector.api.Location? = null
            for (map in partialResults.maps()) {
                val loc = map.getLocation(KEY_LOCATION)
                if (loc != null) {
                    location = loc
                    break
                }
            }

            if (location != null) {
                context.report(
                    issue = ISSUE,
                    location = location,
                    message = "Missing Digital Asset Link for Credential Manager: when using " +
                        "password sign-in through Credential Manager, an `asset_statements` " +
                        "`<meta-data>` element must be declared in the manifest pointing to " +
                        "an asset statements string resource file.",
                )
            } else {
                context.report(
                    issue = ISSUE,
                    location = context.project.manifestFiles.firstOrNull()?.let {
                        com.android.tools.lint.detector.api.Location.create(it)
                    } ?: com.android.tools.lint.detector.api.Location.create(
                        context.project.dir
                    ),
                    message = "Missing Digital Asset Link for Credential Manager: when using " +
                        "password sign-in through Credential Manager, an `asset_statements` " +
                        "`<meta-data>` element must be declared in the manifest pointing to " +
                        "an asset statements string resource file.",
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // In non-partial analysis mode, check directly
        val map = context.getPartialResults(ISSUE).map()
        val usesPasswordCredentials = map.getBoolean(KEY_USES_PASSWORD_CREDENTIALS) == true

        if (!usesPasswordCredentials) {
            return
        }

        if (!hasAssetStatementsMetaData(context)) {
            val location = map.getLocation(KEY_LOCATION)
                ?: context.project.manifestFiles.firstOrNull()?.let {
                    com.android.tools.lint.detector.api.Location.create(it)
                }
                ?: com.android.tools.lint.detector.api.Location.create(context.project.dir)

            context.report(
                issue = ISSUE,
                location = location,
                message = "Missing Digital Asset Link for Credential Manager: when using " +
                    "password sign-in through Credential Manager, an `asset_statements` " +
                    "`<meta-data>` element must be declared in the manifest pointing to " +
                    "an asset statements string resource file.",
            )
        }
    }

    /**
     * Checks whether the merged manifest contains a `<meta-data>` element with
     * `android:name` containing "asset_statements".
     */
    private fun hasAssetStatementsMetaData(context: Context): Boolean {
        val mainProject = context.project
        val mergedManifest = mainProject.mergedManifest ?: return false
        val document = mergedManifest.documentElement ?: return false

        return hasAssetStatementsInElement(document)
    }

    private fun hasAssetStatementsInElement(element: Element): Boolean {
        val tagName = element.tagName
        if (tagName == "meta-data") {
            val name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android",
                "name"
            ).ifEmpty {
                element.getAttribute("android:name")
            }
            if (name.contains(ASSET_STATEMENTS_META_DATA, ignoreCase = true)) {
                return true
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (hasAssetStatementsInElement(child)) {
                    return true
                }
            }
        }
        return false
    }
}
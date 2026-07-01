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
                for details on how to add Digital Asset Links support.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Credential Manager password-related classes
        private val PASSWORD_CREDENTIAL_CLASSES = listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.PasswordCredential",
            "androidx.credentials.CreatePasswordRequest",
        )

        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"
        private const val KEY_HAS_DAL_META_DATA = "hasDalMetaData"

        // The meta-data name for Digital Asset Links
        private const val DAL_META_DATA_NAME = "asset_statements"
    }

    override fun getApplicableConstructorTypes(): List<String> = PASSWORD_CREDENTIAL_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Record that this project uses password credentials
        val lintMap = context.getPartialResults(ISSUE).map()
        lintMap.put(KEY_USES_PASSWORD_CREDENTIAL, true)

        // Store location for potential reporting
        if (!lintMap.containsKey("location")) {
            lintMap.put("location", context.getLocation(node))
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var usesPasswordCredential = false

        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) == true) {
                usesPasswordCredential = true
                break
            }
        }

        if (!usesPasswordCredential) return

        // Check if the manifest has the required meta-data for Digital Asset Links
        val hasDalMetaData = checkManifestForDalMetaData(context)

        if (!hasDalMetaData) {
            // Find the first location to report the issue
            var reportLocation: com.android.tools.lint.detector.api.Location? = null
            for ((_, map) in partialResults) {
                val loc = map.getLocation("location")
                if (loc != null) {
                    reportLocation = loc
                    break
                }
            }

            val incident = com.android.tools.lint.detector.api.Incident(
                ISSUE,
                reportLocation ?: context.project.dir.let {
                    com.android.tools.lint.detector.api.Location.create(it)
                },
                "When using Credential Manager password sign-in, you must declare an " +
                    "asset statements `<meta-data>` element in the manifest that references " +
                    "the `assetlinks.json` file. See " +
                    "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
            )
            context.report(incident)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }

    private fun checkManifestForDalMetaData(context: Context): Boolean {
        val mainProject = context.project
        val manifestFiles = mutableListOf<java.io.File>()

        // Collect manifest files from the project and its dependencies
        val projects = mutableListOf(mainProject)
        projects.addAll(mainProject.allLibraries)

        for (project in projects) {
            val manifestFile = project.manifestFiles
            manifestFiles.addAll(manifestFile)
        }

        for (manifestFile in manifestFiles) {
            if (manifestFile.exists() && manifestContainsDalMetaData(manifestFile)) {
                return true
            }
        }

        return false
    }

    private fun manifestContainsDalMetaData(manifestFile: java.io.File): Boolean {
        return try {
            val content = manifestFile.readText()
            // Check for asset_statements meta-data in the manifest
            // The meta-data element should have android:name containing "asset_statements"
            content.contains(DAL_META_DATA_NAME)
        } catch (e: Exception) {
            false
        }
    }
}
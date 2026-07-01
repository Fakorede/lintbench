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
                for details on how to add the required Digital Asset Link support.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        // Credential Manager password credential types that require DAL
        private val PASSWORD_CREDENTIAL_CLASSES = listOf(
            "androidx.credentials.PasswordCredential",
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest",
        )

        // Key used to store whether password credential usage was found in partial results
        private const val KEY_USES_PASSWORD_CREDENTIAL = "usesPasswordCredential"

        // The meta-data name that must be present in the manifest for DAL support
        private const val DAL_META_DATA_NAME = "asset_statements"

        // Manifest meta-data element name
        private const val META_DATA_ELEMENT = "meta-data"

        // Android namespace attribute
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        // Name attribute
        private const val ATTR_NAME = "name"
    }

    override fun getApplicableConstructorTypes(): List<String> = PASSWORD_CREDENTIAL_CLASSES

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        // Record that we found a usage of a password credential constructor
        val map = context.getPartialResults(ISSUE).map()
        map.put(KEY_USES_PASSWORD_CREDENTIAL, true)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Check if any module reported password credential usage
        var usesPasswordCredential = false
        for (map in partialResults) {
            if (map.getBoolean(KEY_USES_PASSWORD_CREDENTIAL) == true) {
                usesPasswordCredential = true
                break
            }
        }

        if (!usesPasswordCredential) {
            return
        }

        // Check if the manifest contains the required DAL meta-data
        val hasDalMetaData = checkManifestForDalMetaData(context)

        if (!hasDalMetaData) {
            context.report(
                issue = ISSUE,
                location = context.project.manifestFile?.let { context.getLocation(it) }
                    ?: return,
                message = "When using Credential Manager with password sign-in, you must declare " +
                    "an `asset_statements` `<meta-data>` element in the manifest pointing to a " +
                    "string resource file that includes the `assetlinks.json` files to load.",
            )
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Only run in non-partial analysis mode (single module projects)
        if (context.isGlobalAnalysis()) {
            val partialResults = context.getPartialResults(ISSUE)
            checkPartialResults(context, partialResults)
        }
    }

    /**
     * Checks the merged manifest for the presence of a DAL meta-data element.
     * The meta-data element should have android:name="asset_statements".
     */
    private fun checkManifestForDalMetaData(context: Context): Boolean {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return false

        val documentElement = mergedManifest.documentElement ?: return false

        // Walk through the manifest document looking for meta-data elements
        return containsDalMetaData(documentElement)
    }

    /**
     * Recursively searches for a meta-data element with the DAL asset_statements name.
     */
    private fun containsDalMetaData(element: org.w3c.dom.Element): Boolean {
        if (element.tagName == META_DATA_ELEMENT || element.localName == META_DATA_ELEMENT) {
            val nameAttr = element.getAttributeNS(ANDROID_NS, ATTR_NAME)
                .takeIf { it.isNotEmpty() }
                ?: element.getAttribute(ATTR_NAME)
            if (nameAttr == DAL_META_DATA_NAME) {
                return true
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element) {
                if (containsDalMetaData(child)) {
                    return true
                }
            }
        }

        return false
    }
}
package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
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
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var usesPasswordCredentials = false
    private var metaDataLocation: Location? = null
    private var metaDataHasResourceAttribute = false
    private var assetStatementsResourceName: String? = null
    private var applicationLocation: Location? = null
    private val stringResources = mutableMapOf<String, Pair<String, Location>>()

    override fun beforeCheckEachProject(context: Context) {
        usesPasswordCredentials = false
        metaDataLocation = null
        metaDataHasResourceAttribute = false
        assetStatementsResourceName = null
        applicationLocation = null
        stringResources.clear()
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.GetPasswordOption",
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.PasswordCredential"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        usesPasswordCredentials = true
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("application", "meta-data", "string")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.XML
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                applicationLocation = context.getNameLocation(element)
            }
            "meta-data" -> {
                val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name").takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:name")
                if (name == "asset_statements") {
                    metaDataLocation = context.getLocation(element)
                    val resource = element.getAttributeNS("http://schemas.android.com/apk/res/android", "resource").takeIf { it.isNotEmpty() }
                        ?: element.getAttribute("android:resource")
                    if (resource.isNotEmpty()) {
                        metaDataHasResourceAttribute = true
                        val prefix = "@string/"
                        if (resource.startsWith(prefix)) {
                            assetStatementsResourceName = resource.substring(prefix.length)
                        }
                    } else {
                        metaDataHasResourceAttribute = false
                    }
                }
            }
            "string" -> {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    val value = element.textContent
                    stringResources[name] = Pair(value, context.getLocation(element))
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val hasMetaData = metaDataLocation != null

        if (usesPasswordCredentials && !hasMetaData) {
            val location = applicationLocation ?: Location.create(context.project.dir)
            context.report(
                ISSUE,
                location,
                "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password sign-in"
            )
            return
        }

        if (hasMetaData) {
            if (!metaDataHasResourceAttribute) {
                context.report(
                    ISSUE,
                    metaDataLocation!!,
                    "The `asset_statements` meta-data element must have an `android:resource` attribute pointing to a string resource"
                )
                return
            }

            val resourceName = assetStatementsResourceName
            if (resourceName == null) {
                context.report(
                    ISSUE,
                    metaDataLocation!!,
                    "The `android:resource` attribute must point to a string resource (e.g., `@string/asset_statements`)"
                )
                return
            }

            val resourceData = stringResources[resourceName]
            if (resourceData == null) {
                if (context.scope.contains(Scope.RESOURCE_FILE)) {
                    context.report(
                        ISSUE,
                        metaDataLocation!!,
                        "String resource `@string/$resourceName` not found"
                    )
                }
                return
            }

            val (stringValue, stringLocation) = resourceData
            val includePattern = java.util.regex.Pattern.compile("\"include\"\\s*:\\s*\"([^\"]*)\"")
            val matcher = includePattern.matcher(stringValue)
            if (!matcher.find()) {
                context.report(
                    ISSUE,
                    stringLocation,
                    "The asset statements string resource must include the `assetlinks.json` files to load"
                )
            } else {
                val url = matcher.group(1)
                if (url.isNullOrBlank() || !url.startsWith("https://")) {
                    context.report(
                        ISSUE,
                        stringLocation,
                        "Missing or invalid URL in asset statements `include` statement"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, you must associate your app \
                with a website by declaring a Digital Asset Link. This is done by adding a `<meta-data>` \
                element with `android:name="asset_statements"` pointing to a string resource containing \
                the asset statements in your `AndroidManifest.xml`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
        )
    }
}
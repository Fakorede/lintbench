package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val KEY_HAS_PASSWORD_SIGN_IN = "hasPasswordSignIn"
        private const val KEY_LOCATIONS = "locations"

        @JvmField
        val ISSUE = Issue.create(
            id = "CredManMissingDal",
            briefDescription = "Missing Digital Asset Link for Credential Manager",
            explanation = """
                When using password sign-in through Credential Manager, an asset statements string resource file \
                that includes the `assetlinks.json` files to load must be declared in the manifest using a \
                `<meta-data>` element.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CredentialManagerDigitalAssetLinkDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.GetPasswordOption"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        candidate: PsiMethod
    ) {
        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map(context.project)
        map.put(KEY_HAS_PASSWORD_SIGN_IN, true)

        val range = context.getLocation(node)
        val startOffset = range.start?.offset ?: 0
        val endOffset = range.end?.offset ?: 0
        val filePath = context.file.absolutePath

        val locs = map.getString(KEY_LOCATIONS)
        val entry = "$filePath:$startOffset:$endOffset"
        if (locs == null) {
            map.put(KEY_LOCATIONS, entry)
        } else {
            map.put(KEY_LOCATIONS, "$locs;$entry")
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        val mainProject = context.mainProject
        val document = mainProject.mergedManifest ?: return

        var hasAssetStatements = false
        val metaDatas = document.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val element = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name").takeIf { it.isNotEmpty() }
                ?: element.getAttribute("android:name")
            if (name == "asset_statements") {
                hasAssetStatements = true
                break
            }
        }

        if (hasAssetStatements) {
            return
        }

        for (project in context.projects) {
            val map = partialResults.map(project)
            if (map.getBoolean(KEY_HAS_PASSWORD_SIGN_IN) == true) {
                val locs = map.getString(KEY_LOCATIONS) ?: continue
                val entries = locs.split(';')
                for (entry in entries) {
                    if (entry.isEmpty()) continue
                    val parts = entry.split(':')
                    if (parts.size >= 3) {
                        val filePath = parts.subList(0, parts.size - 2).joinToString(":")
                        val start = parts[parts.size - 2].toIntOrNull() ?: 0
                        val end = parts[parts.size - 1].toIntOrNull() ?: 0

                        val file = java.io.File(filePath)
                        val contents = context.client.getCharSequence(file) ?: ""
                        val location = Location.create(file, contents, start, end)

                        val incident = Incident(
                            ISSUE,
                            location,
                            "Missing Digital Asset Link declaration in AndroidManifest.xml for Credential Manager password sign-in"
                        )
                        context.report(incident)
                    }
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op, analysis handled in checkPartialResults
    }
}
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Location
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
                When using password sign-in through Credential Manager, you must declare \
                an asset statements string resource file that includes the `assetlinks.json` \
                files to load. This must be declared in the manifest using a `<meta-data>` \
                element with `android:name="asset_statements"`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            "androidx.credentials.CreatePasswordRequest",
            "androidx.credentials.GetPasswordOption"
        )
    }

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val partialResults = context.getPartialResults(ISSUE)
        val map = partialResults.map(context.project)
        map.put("hasPasswordSignIn", true)

        val location = context.getLocation(node)
        val file = location.file.absolutePath
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        val newCall = "$file:$start:$end"
        val existing = map.getString("calls")
        val updated = if (existing == null) newCall else "$existing;$newCall"
        map.put("calls", updated)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasPasswordSignIn = false
        val calls = mutableListOf<String>()

        for (project in partialResults.projects()) {
            val map = partialResults.map(project)
            if (map.getBoolean("hasPasswordSignIn") == true) {
                hasPasswordSignIn = true
                val callsStr = map.getString("calls")
                if (callsStr != null) {
                    for (call in callsStr.split(";")) {
                        if (call.isNotEmpty()) {
                            calls.add(call)
                        }
                    }
                }
            }
        }

        if (hasPasswordSignIn) {
            val manifest = context.mainProject.mergedManifest
            if (!hasAssetStatements(manifest)) {
                if (calls.isNotEmpty()) {
                    for (call in calls) {
                        val parts = call.split(":")
                        if (parts.size == 3) {
                            val filePath = parts[0]
                            val start = parts[1].toIntOrNull() ?: -1
                            val end = parts[2].toIntOrNull() ?: -1
                            if (filePath.isNotEmpty() && start != -1 && end != -1) {
                                val file = java.io.File(filePath)
                                if (file.exists()) {
                                    val contents = try {
                                        file.readText()
                                    } catch (e: java.lang.Exception) {
                                        ""
                                    }
                                    val location = if (contents.isNotEmpty() && start >= 0 && end <= contents.length && start <= end) {
                                        Location.create(file, contents, start, end)
                                    } else {
                                        Location.create(file)
                                    }
                                    context.report(
                                        ISSUE,
                                        location,
                                        "Missing Digital Asset Link for Credential Manager password sign-in"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    context.report(
                        ISSUE,
                        Location.create(context.mainProject.dir),
                        "Missing Digital Asset Link for Credential Manager password sign-in"
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Handled via checkPartialResults
    }

    private fun hasAssetStatements(manifest: org.w3c.dom.Document?): Boolean {
        val root = manifest?.documentElement ?: return false
        val applicationList = root.getElementsByTagName("application")
        if (applicationList.length == 0) return false
        val application = applicationList.item(0) as? org.w3c.dom.Element ?: return false
        val metaDatas = application.getElementsByTagName("meta-data")
        for (i in 0 until metaDatas.length) {
            val element = metaDatas.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "asset_statements") {
                return true
            }
            val nameNoNs = element.getAttribute("android:name")
            if (nameNoNs == "asset_statements") {
                return true
            }
        }
        return false
    }
}
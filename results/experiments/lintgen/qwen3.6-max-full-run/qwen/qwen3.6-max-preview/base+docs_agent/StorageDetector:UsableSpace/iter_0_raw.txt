package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class StorageDetector : Detector(), SourceCodeScanner {
    private val filesWithNewApi = mutableSetOf<String>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        "getUsableSpace",
        "getAllocatableBytes",
        "allocateBytes"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val filePath = context.file?.path ?: return
        val methodName = method.name

        if (methodName == "getAllocatableBytes" || methodName == "allocateBytes") {
            filesWithNewApi.add(filePath)
        } else if (methodName == "getUsableSpace") {
            if (!filesWithNewApi.contains(filePath)) {
                context.report(
                    ISSUE,
                    context.getLocation(node),
                    "Use `getAllocatableBytes(UUID)` or `allocateBytes(FileDescriptor, long)` instead of `getUsableSpace()`"
                )
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        context.file?.path?.let { filesWithNewApi.remove(it) }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new  `allocateBytes(FileDescriptor, long)` API, which will automatically clear  cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data,  call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since  the former will consider any cached data that the system is willing to  clear on your behalf.

                Note that these methods require API level 26. If your app is running on  older devices, you will probably need to use both APIs, conditionally switching  on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to  see if you are already using both APIs, so if it warns even though you are  already using the new API, consider moving the calls to the same file or  suppressing the warning.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using `getUsableSpace()`",
            explanation = """
                When you need to allocate disk space for large files, consider using the new \
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear \
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, \
                call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since \
                the former will consider any cached data that the system is willing to \
                clear on your behalf.

                Note that these methods require API level 26. If your app is running on \
                older devices, you will probably need to use both APIs, conditionally switching \
                on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to \
                see if you are already using both APIs, so if it warns even though you are \
                already using the new API, consider moving the calls to the same file or \
                suppressing the warning.
                """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"

        private val NEW_STORAGE_METHODS = setOf(GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if the method is getUsableSpace on java.io.File
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName
        if (qualifiedName != "java.io.File") {
            return
        }

        // Check if the same file already uses the new storage APIs
        if (fileUsesNewStorageApi(context, node)) {
            return
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = "Consider using `getAllocatableBytes(UUID)` instead of " +
                "`getUsableSpace()` since the former will consider any cached data " +
                "that the system is willing to clear on your behalf"
        )
    }

    /**
     * Checks whether the file containing [node] also calls any of the new
     * storage allocation APIs (getAllocatableBytes or allocateBytes).
     */
    private fun fileUsesNewStorageApi(context: JavaContext, node: UCallExpression): Boolean {
        val uFile = context.uastFile ?: return false
        val visitor = NewStorageApiVisitor()
        uFile.accept(visitor)
        return visitor.foundNewApi
    }

    /**
     * UAST visitor that looks for calls to the new storage management APIs.
     */
    private class NewStorageApiVisitor : AbstractUastVisitor() {
        var foundNewApi = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (foundNewApi) {
                // Short-circuit: already found what we need
                return true
            }
            val methodName = node.methodName
            if (methodName != null && methodName in NEW_STORAGE_METHODS) {
                foundNewApi = true
                return true
            }
            return false
        }
    }
}
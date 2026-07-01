package com.android.tools.lint.detector.api

import com.android.resources.ResourceFolderType
import java.io.File

class ResourceContext(
    override val driver: LintDriver,
    override val project: Project,
    val file: File,
    val folderType: ResourceFolderType,
    val resource: Resource
) : Context() {
    ...
}
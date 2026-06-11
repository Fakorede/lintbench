package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class VectorDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "VectorImageGeneration",
            "Checks for vector images that may not be fully supported on older devices.",
            "Vector icons require API 21 or API 24 depending on used features. When `minSdkVersion` is less than 21 or 24 and Android Gradle plugin 1.4 or higher is used, a vector drawable placed in the `drawable` folder is automatically moved to `drawable-anydpi-v21` or `drawable-anydpi-v24` and bitmap images are generated for different screen resolutions for backwards compatibility.\n" +
            "However, there are some limitations to this raster image generation. This lint check flags elements and attributes that are not fully supported.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(VectorDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Project project = context.getProject();
        int minSdkVersion = getMinSdkVersion(project);

        if (minSdkVersion < 21 || minSdkVersion < 24) {
            String path = context.getDriver().getFile().getCanonicalPath();

            // Check if the drawable is in the correct folder for backwards compatibility
            boolean isInCorrectFolder = false;
            ResourceFolderType type = ResourceFolderType.fromFileName(path);
            if (type == ResourceFolderType.DRAWABLE) {
                String folderName = getResourceFolderName(path);
                isInCorrectFolder = folderName.contains("anydpi-v21") || folderName.contains("anydpi-v24");
            }

            if (!isInCorrectFolder) {
                Location location = context.getLocation(element);
                context.report(ISSUE, element, location, "Vector drawable is not in the correct folder for backwards compatibility.");
            }
        }
    }

    private int getMinSdkVersion(Project project) {
        return project.getModule().getProject().getExtension("android").getVariantManager().getCurrentVariant().getMergedFlavor().getMinSdkVersion();
    }

    private String getResourceFolderName(String path) {
        int start = path.lastIndexOf("/res/");
        int end = path.indexOf('/', start + 5);
        if (end == -1) {
            end = path.length();
        }
        return path.substring(start, end);
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.android.utils.SdkVersionInfo;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiDirectory;
import org.w3c.dom.Element;

import java.util.List;

public class IconDetector extends ResourceXmlScanner {

    private static final String WEBP_EXTENSION = ".webp";

    @NonNull
    @Override
    public List<Pair<String, Class<? extends Element>>> getElementsToCheck() {
        return ImmutableList.of(
                Pair.of("item", null),
                Pair.of("drawable", null)
        );
    }

    @Override
    public void visitElement(@NonNull Context context, @NonNull Element element) {
        String resourceType = getResourceFolderType(context).getName();
        if (resourceType.equals(ResourceFolderType.DRAWABLE.getName())) {
            checkWebPUsage(context, element);
        }
    }

    private void checkWebPUsage(@NonNull Context context, @NonNull Element element) {
        PsiDirectory directory = getDirectory(context);
        String resourceName = getResourceName(context);

        if (directory != null && resourceName != null) {
            String resourcePath = getFilePath(context);
            if (resourcePath.endsWith(WEBP_EXTENSION)) {
                int minSdkVersion = context.getProject().getModuleSystem().getMinApiLevel();
                if (minSdkVersion < SdkVersionInfo.MIN_API_LEVEL_JELLY_BEAN_MR1) {
                    context.report(
                            ISSUE_WEBP_UNSUPPORTED,
                            element,
                            context.getLocation(element),
                            "WebP format is not supported below Android 4.0 (API level 15)"
                    );
                } else if (minSdkVersion < SdkVersionInfo.MIN_API_LEVEL_JELLY_BEAN_MR2) {
                    context.report(
                            ISSUE_WEBP_FEATURES_UNSUPPORTED,
                            element,
                            context.getLocation(element),
                            "WebP features such as lossless encoding and transparency are not supported below Android 4.2.1 (API level 18)"
                    );
                }
            }
        }
    }

    private static final Issue ISSUE_WEBP_UNSUPPORTED = Issue.create(
            "WebPUnsupported",
            "The WebP format requires Android 4.0 (API 15).",
            "This issue indicates that the use of WebP images is not supported on devices below API level 15.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final Issue ISSUE_WEBP_FEATURES_UNSUPPORTED = Issue.create(
            "WebPFeaturesUnsupported",
            "Certain WebP features such as lossless encoding and transparency require Android 4.2.1 (API 18).",
            "This issue indicates that the use of certain WebP features is not supported on devices below API level 18.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );
}
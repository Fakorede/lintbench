package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.Locale;
import org.jetbrains.annotations.NonNull;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue CONVERT_TO_WEBP = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n" +
            "\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        String lower = name.toLowerCase(Locale.US);
        
        if (lower.endsWith(".9.png")) {
            return;
        }
        
        boolean isPng = lower.endsWith(".png");
        boolean isJpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        
        if (!isPng && !isJpg) {
            return;
        }
        
        int minSdk = 1;
        if (context.getProject().getMinSdkVersion() != null) {
            minSdk = context.getProject().getMinSdkVersion().getFeatureLevel();
        }
        
        // WebP is supported from API 15 (lossy) and API 18 (lossless/transparency)
        if (minSdk < 15) {
            return;
        }
        
        String format = isPng ? "PNG" : "JPEG";
        String message = String.format("The image %s can be converted to WebP to save space.", file.getName());
        
        context.report(
                CONVERT_TO_WEBP,
                Location.create(file),
                message,
                null
        );
    }
}
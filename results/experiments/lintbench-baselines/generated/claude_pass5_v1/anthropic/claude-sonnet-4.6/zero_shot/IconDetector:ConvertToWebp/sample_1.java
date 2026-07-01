package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;

/**
 * Checks for image files that could be converted to WebP format.
 */
public class IconDetector extends ResourceXmlDetector {

    /** Convert images to WebP */
    public static final Issue WEBP_ELIGIBLE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n" +
            "\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            1,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.BINARY_RESOURCE_FILE)
            )
    );

    private static final String PNG_EXTENSION = ".png";
    private static final String JPG_EXTENSION = ".jpg";
    private static final String JPEG_EXTENSION = ".jpeg";
    private static final String NINE_PATCH_EXTENSION = ".9.png";
    private static final String WEBP_EXTENSION = ".webp";

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName().toLowerCase();

        // Skip nine-patch files
        if (name.endsWith(NINE_PATCH_EXTENSION)) {
            return;
        }

        // Skip WebP files (already converted)
        if (name.endsWith(WEBP_EXTENSION)) {
            return;
        }

        boolean isPng = name.endsWith(PNG_EXTENSION);
        boolean isJpeg = name.endsWith(JPG_EXTENSION) || name.endsWith(JPEG_EXTENSION);

        if (!isPng && !isJpeg) {
            return;
        }

        // Check that we're in a drawable or mipmap folder
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();
        if (!parentName.startsWith("drawable") && !parentName.startsWith("mipmap")) {
            return;
        }

        // Check minimum SDK version - WebP with transparency/lossless requires API 18 (4.3)
        // Basic WebP support requires API 17 (4.2)
        // For lossless + transparency: API 18
        // We'll flag for projects targeting API 18+
        int minSdk = context.getProject().getMinSdk();

        if (minSdk < 18) {
            // WebP with lossless + transparency requires API 18
            // For PNG files (which may have transparency), require API 18
            // For JPEG files (no transparency), basic WebP requires API 17
            if (isPng && minSdk < 18) {
                return;
            }
            if (isJpeg && minSdk < 17) {
                return;
            }
        }

        String message;
        if (isPng) {
            message = String.format(
                    "One or more images in this project can be converted to the WebP format "
                    + "which typically results in smaller file sizes, even for lossless "
                    + "conversion. Tools > Convert to WebP... shows a preview with loss "
                    + "information.");
        } else {
            message = String.format(
                    "One or more images in this project can be converted to the WebP format "
                    + "which typically results in smaller file sizes, even for lossless "
                    + "conversion. Tools > Convert to WebP... shows a preview with loss "
                    + "information.");
        }

        Location location = Location.create(file);
        context.report(WEBP_ELIGIBLE, location, message);
    }
}
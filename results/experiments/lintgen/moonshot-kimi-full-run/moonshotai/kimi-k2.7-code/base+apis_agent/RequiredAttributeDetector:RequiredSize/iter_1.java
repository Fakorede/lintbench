package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Element;

public class RequiredAttributeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing layout_width or layout_height attributes",
                    "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                            + "There is a runtime check for this, so if you fail to specify a size, an exception "
                            + "is thrown at runtime.\n\n"
                            + "It's possible to specify these widths via styles as well. GridLayout, as a special "
                            + "case, does not require you to specify a size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (tag.equals("GridLayout") || tag.endsWith(".GridLayout")) {
            return;
        }

        if (!isView(element)) {
            return;
        }

        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (!hasWidth && !hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attributes `android:layout_width` and `android:layout_height`");
        } else if (!hasWidth) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:layout_width`");
        } else if (!hasHeight) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing required attribute `android:layout_height`");
        }
    }

    private static boolean isView(@NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals(VIEW_TAG)) {
            return true;
        }
        if (tag.isEmpty()) {
            return false;
        }
        char first = tag.charAt(0);
        return Character.isUpperCase(first) || tag.indexOf('.') != -1;
    }

    public static boolean hasLayoutVariations(@NonNull File file) {
        File folder = file.getParentFile();
        if (folder == null) {
            return false;
        }

        String folderName = folder.getName();
        if (!folderName.equals("layout") && !folderName.startsWith("layout-")) {
            return false;
        }

        String fileName = file.getName();
        File res = folder.getParentFile();
        if (res == null) {
            return false;
        }

        File[] siblings = res.listFiles();
        if (siblings == null) {
            return false;
        }

        for (File sibling : siblings) {
            if (sibling == folder || !sibling.isDirectory()) {
                continue;
            }
            String siblingName = sibling.getName();
            if ((siblingName.equals("layout") || siblingName.startsWith("layout-"))
                    && new File(sibling, fileName).exists()) {
                return true;
            }
        }

        return false;
    }
}
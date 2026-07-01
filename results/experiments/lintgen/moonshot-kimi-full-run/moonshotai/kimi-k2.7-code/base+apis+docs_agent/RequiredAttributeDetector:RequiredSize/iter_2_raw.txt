package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            RequiredAttributeDetector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout width or height",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "These attributes may also be supplied through a style. "
                    + "`GridLayout` does not require an explicit size.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (tag == null || tag.isEmpty()) {
            return;
        }

        if (isSpecialTag(tag, element)) {
            return;
        }

        if (element.hasAttribute(ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (hasWidth && hasHeight) {
            return;
        }

        List<String> missing = new ArrayList<>(2);
        if (!hasWidth) {
            missing.add(ATTR_LAYOUT_WIDTH);
        }
        if (!hasHeight) {
            missing.add(ATTR_LAYOUT_HEIGHT);
        }

        String message;
        if (missing.size() == 1) {
            message = "Missing required `" + missing.get(0) + "` attribute";
        } else {
            message = "Missing required `layout_width` and `layout_height` attributes";
        }

        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private static boolean isSpecialTag(@NotNull String tag, @NotNull Element element) {
        if (tag.equals("include")
                || tag.equals("merge")
                || tag.equals("requestFocus")
                || tag.equals("fragment")
                || tag.equals("layout")
                || tag.equals("data")
                || tag.equals("variable")
                || tag.equals("import")) {
            return true;
        }

        if (tag.equals("view")) {
            String className = element.getAttribute(ATTR_CLASS);
            return className != null && className.endsWith("GridLayout");
        }

        return tag.equals("GridLayout") || tag.endsWith(".GridLayout");
    }

    public static boolean hasLayoutVariations(@NotNull File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String parentName = parent.getName();
        if (!parentName.startsWith("layout")) {
            return false;
        }
        File resDir = parent.getParentFile();
        if (resDir == null) {
            return false;
        }
        File[] children = resDir.listFiles();
        if (children == null) {
            return false;
        }
        String name = file.getName();
        for (File child : children) {
            String childName = child.getName();
            if (childName.startsWith("layout")
                    && !childName.equals(parentName)
                    && child.isDirectory()) {
                File variant = new File(child, name);
                if (variant.exists()) {
                    return true;
                }
            }
        }
        return false;
    }
}
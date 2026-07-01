package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (isNonViewTag(tag)) {
            return;
        }

        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
            return;
        }

        if (isGridLayout(tag)) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent instanceof Element && isGridLayout(((Element) parent).getTagName())) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String missing;
            if (!hasWidth && !hasHeight) {
                missing = SdkConstants.ATTR_LAYOUT_WIDTH + " and " + SdkConstants.ATTR_LAYOUT_HEIGHT;
            } else {
                missing = !hasWidth ? SdkConstants.ATTR_LAYOUT_WIDTH : SdkConstants.ATTR_LAYOUT_HEIGHT;
            }
            context.report(ISSUE, context.getLocation(element),
                    "Missing required attribute: " + missing);
        }
    }

    private static boolean isNonViewTag(String tag) {
        return "merge".equals(tag) ||
               "include".equals(tag) ||
               "fragment".equals(tag) ||
               "requestFocus".equals(tag) ||
               "tag".equals(tag) ||
               "layout".equals(tag);
    }

    private static boolean isGridLayout(String tag) {
        return "GridLayout".equals(tag) ||
               "androidx.gridlayout.widget.GridLayout".equals(tag) ||
               "android.support.v7.widget.GridLayout".equals(tag);
    }

    public static boolean hasLayoutVariations(File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            String name = file.getName();
            File res = parent.getParentFile();
            if (res != null) {
                File[] folders = res.listFiles();
                if (folders != null) {
                    for (File folder : folders) {
                        if (folder.isDirectory() && folder != parent) {
                            String folderName = folder.getName();
                            if (folderName.startsWith(SdkConstants.FD_RES_LAYOUT)
                                    && new File(folder, name).exists()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
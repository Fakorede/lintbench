package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class RequiredAttributeDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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

        // Skip non-view layout tags
        if ("merge".equals(tag) ||
            "include".equals(tag) ||
            "requestFocus".equals(tag) ||
            "tag".equals(tag) ||
            "fragment".equals(tag)) {
            return;
        }

        // GridLayout does not require layout_width/layout_height
        if (tag.endsWith("GridLayout")) {
            return;
        }

        // If a style is applied, dimensions might be defined there
        if (element.hasAttribute(SdkConstants.ATTR_STYLE) ||
            element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_STYLE)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            StringBuilder message = new StringBuilder("Missing required attribute");
            if (!hasWidth && !hasHeight) {
                message.append("s `layout_width` and `layout_height`");
            } else if (!hasWidth) {
                message.append(" `layout_width`");
            } else {
                message.append(" `layout_height`");
            }
            context.report(ISSUE, element, context.getLocation(element), message.toString());
        }
    }

    public static boolean hasLayoutVariations(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String name = file.getName();
        File resDir = parent.getParentFile();
        if (resDir == null) {
            return false;
        }
        File[] folders = resDir.listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory() && folder != parent && folder.getName().startsWith("layout")) {
                    if (new File(folder, name).exists()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
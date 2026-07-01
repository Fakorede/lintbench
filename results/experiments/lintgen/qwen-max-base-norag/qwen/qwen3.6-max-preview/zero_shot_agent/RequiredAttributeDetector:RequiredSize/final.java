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
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Element;

import java.io.File;
import java.util.Collection;

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
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if ("merge".equals(tag) ||
            "include".equals(tag) ||
            "requestFocus".equals(tag) ||
            "tag".equals(tag) ||
            "fragment".equals(tag)) {
            return;
        }

        if (tag.endsWith(SdkConstants.GRID_LAYOUT)) {
            return;
        }

        if (element.hasAttribute(SdkConstants.ATTR_STYLE)) {
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
            context.report(ISSUE, context.getLocation(element), message.toString());
        }
    }

    public static boolean hasLayoutVariations(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        String folderName = parent.getName();
        int index = folderName.indexOf('-');
        if (index != -1) {
            folderName = folderName.substring(0, index);
        }
        File resourceDir = parent.getParentFile();
        if (resourceDir != null) {
            File[] files = resourceDir.listFiles();
            if (files != null) {
                String name = file.getName();
                for (File resFolder : files) {
                    if (resFolder.isDirectory()) {
                        String resFolderName = resFolder.getName();
                        if (resFolderName.startsWith(folderName)
                                && resFolderName.length() > folderName.length()
                                && resFolderName.charAt(folderName.length()) == '-') {
                            if (new File(resFolder, name).exists()) {
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
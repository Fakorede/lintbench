package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing layout_width or layout_height attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime.\n\n" +
            "It's possible to specify these widths via styles as well. GridLayout, as a special " +
            "case, does not require you to specify a size.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag.equals("merge") || tag.equals("include") || tag.equals("requestFocus") ||
            tag.equals("tag") || tag.equals("fragment")) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.endsWith("GridLayout")) {
                return;
            }
        }

        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing required layout_width attribute");
        }
        if (!hasHeight) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing required layout_height attribute");
        }
    }

    public static boolean hasLayoutVariations(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        File res = parent.getParentFile();
        if (res == null) {
            return false;
        }
        String name = file.getName();
        File[] folders = res.listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory() && !folder.equals(parent)) {
                    String folderName = folder.getName();
                    if (folderName.startsWith(SdkConstants.FD_RES_LAYOUT)) {
                        File variation = new File(folder, name);
                        if (variation.exists()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
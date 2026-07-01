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
import java.io.File;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. " +
            "There is a runtime check for this, so if you fail to specify a size, an exception " +
            "is thrown at runtime. It's possible to specify these widths via styles as well. " +
            "GridLayout, as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tagName = element.getTagName();
        if (tagName.equals("merge") || 
            tagName.equals("include") || 
            tagName.equals("requestFocus") ||
            tagName.equals("tag") || 
            tagName.equals("layout") || 
            tagName.equals("data") ||
            tagName.equals("variable") || 
            tagName.equals("import")) {
            return;
        }

        // Only check views (usually start with uppercase, or "view", "fragment")
        if (Character.isLowerCase(tagName.charAt(0)) && !tagName.equals("view") && !tagName.equals("fragment")) {
            return;
        }

        // Check if parent is GridLayout (children of GridLayout do not require size attributes)
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals("GridLayout") || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        // If style is specified, it might define width/height
        if (element.hasAttribute("style")) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "The view is missing both `android:layout_width` and `android:layout_height` attributes";
            } else if (!hasWidth) {
                message = "The view is missing the `android:layout_width` attribute";
            } else {
                message = "The view is missing the `android:layout_height` attribute";
            }
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    public static boolean hasLayoutVariations(File file) {
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
        String name = file.getName();
        File[] children = resDir.listFiles();
        if (children != null) {
            for (File child : children) {
                String childName = child.getName();
                if (child.isDirectory() && childName.startsWith("layout") && !childName.equals(parentName)) {
                    if (new File(child, name).exists()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
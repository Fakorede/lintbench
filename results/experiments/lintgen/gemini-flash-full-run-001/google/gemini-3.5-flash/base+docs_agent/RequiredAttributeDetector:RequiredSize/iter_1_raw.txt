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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RequiredSize",
            "Missing `layout_width` or `layout_height` attributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute. "
                    + "There is a runtime check for this, so if you fail to specify a size, "
                    + "an exception is thrown at runtime.\n\n"
                    + "It's possible to specify these widths via styles as well. GridLayout, "
                    + "as a special case, does not require you to specify a size.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    RequiredAttributeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        String tagName = element.getTagName();
        if (tagName.equals(SdkConstants.VIEW_MERGE)
                || tagName.equals("requestFocus")
                || tagName.equals("tag")
                || tagName.equals("fragment")
                || tagName.equals("annotation")
                || tagName.equals("layout")
                || tagName.equals("data")
                || tagName.equals("variable")
                || tagName.equals("import")) {
            return;
        }

        // If parent is a GridLayout, width/height are not required
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            if (parentTag.equals("GridLayout")
                    || parentTag.endsWith(".GridLayout")) {
                return;
            }
        }

        // If style is specified, width/height might be defined there
        if (element.hasAttribute("style")) {
            return;
        }

        boolean isInclude = tagName.equals(SdkConstants.VIEW_INCLUDE);
        boolean hasWidth = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);

        if (isInclude) {
            // For include tags, layout_width/layout_height are only required if
            // other layout attributes are specified.
            boolean hasAnyLayoutAttr = false;
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String localName = attr.getLocalName();
                if (localName != null && localName.startsWith("layout_")
                        && SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())) {
                    if (!localName.equals(SdkConstants.ATTR_LAYOUT_WIDTH)
                            && !localName.equals(SdkConstants.ATTR_LAYOUT_HEIGHT)) {
                        hasAnyLayoutAttr = true;
                        break;
                    }
                }
            }
            if (!hasAnyLayoutAttr) {
                return;
            }
        }

        if (!hasWidth || !hasHeight) {
            String message;
            if (!hasWidth && !hasHeight) {
                message = "The attributes `layout_width` and `layout_height` are missing";
            } else if (!hasWidth) {
                message = "The attribute `layout_width` is missing";
            } else {
                message = "The attribute `layout_height` is missing";
            }
            context.report(ISSUE, element, context.getNameLocation(element), message);
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
        File[] files = res.listFiles();
        if (files != null) {
            int count = 0;
            for (File dir : files) {
                if (dir.isDirectory() && dir.getName().startsWith("layout")) {
                    File variation = new File(dir, name);
                    if (variation.exists()) {
                        count++;
                    }
                }
            }
            return count > 1;
        }
        return false;
    }
}
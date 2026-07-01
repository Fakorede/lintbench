package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
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
import java.util.Collection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root != null) {
            checkElement(context, root);
        }
    }

    private static void checkElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (tag.equals(GRID_LAYOUT) || tag.endsWith(GRID_LAYOUT)) {
            return;
        }

        if (!tag.equals(VIEW_INCLUDE) && !tag.equals(VIEW_MERGE) && isView(element)) {
            if (!element.hasAttribute(ATTR_STYLE)) {
                boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

                if (!hasWidth && !hasHeight) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Missing required attributes 'android:layout_width' and 'android:layout_height'");
                } else if (!hasWidth) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Missing required attribute 'android:layout_width'");
                } else if (!hasHeight) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "Missing required attribute 'android:layout_height'");
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child);
            }
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
}
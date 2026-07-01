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
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class RtlDetector extends Detector implements XmlScanner {

    public static final Issue COMPAT = Issue.create(
        "RtlCompat",
        "Right-to-left text compatibility issues",
        "API 17 adds a `textAlignment` attribute to specify text alignment. However, " +
        "if you are supporting older versions than API 17, you must **also** specify a " +
        "gravity or layout_gravity attribute, since older platforms will ignore the " +
        "`textAlignment` attribute.",
        Category.RTL,
        6,
        Severity.ERROR,
        new Implementation(
            RtlDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    public static final Issue ISSUE = COMPAT;

    public static final Issue ENABLED = Issue.create(
        "RtlEnabled",
        "Using RTL attributes without enabling RTL support",
        "Using RTL attributes without enabling RTL support",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue SYMMETRY = Issue.create(
        "RtlSymmetry",
        "Padding and margin symmetry",
        "Padding and margin symmetry",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final Issue USE_START_END = Issue.create(
        "RtlHardcoded",
        "Using ltr attributes where RTL should be used",
        "Using ltr attributes where RTL should be used",
        Category.RTL,
        5,
        Severity.WARNING,
        new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = new String[] {
        "layout_alignParentLeft", "layout_alignParentStart",
        "layout_alignParentRight", "layout_alignParentEnd",
        "layout_toLeftOf", "layout_toStartOf",
        "layout_toRightOf", "layout_toEndOf",
        "layout_alignLeft", "layout_alignStart",
        "layout_alignRight", "layout_alignEnd",
        "paddingLeft", "paddingStart",
        "paddingRight", "paddingEnd",
        "layout_marginLeft", "layout_marginStart",
        "layout_marginRight", "layout_marginEnd",
        "drawableLeft", "drawableStart",
        "drawableRight", "drawableEnd"
    };

    public static boolean isRtlAttributeName(String name) {
        return name.contains("Start") || name.contains("End");
    }

    public static String convertOldToNew(String attribute) {
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Start");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "End");
        }
        return attribute;
    }

    public static String convertNewToOld(String attribute) {
        if (attribute.contains("Start")) {
            return attribute.replace("Start", "Left");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Right");
        }
        return attribute;
    }

    public static String convertToOppositeDirection(String attribute) {
        if (attribute.contains("Left")) {
            return attribute.replace("Left", "Right");
        } else if (attribute.contains("Right")) {
            return attribute.replace("Right", "Left");
        } else if (attribute.contains("Start")) {
            return attribute.replace("Start", "End");
        } else if (attribute.contains("End")) {
            return attribute.replace("End", "Start");
        }
        return attribute;
    }

    public static int getFolderVersion(File file) {
        int version = getFolderVersion(file.getName());
        if (version != -1) {
            return version;
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return getFolderVersion(parent.getName());
        }
        return -1;
    }

    public static int getFolderVersion(String folderName) {
        if (folderName == null) {
            return -1;
        }
        int index = folderName.lastIndexOf("-v");
        if (index != -1 && index + 2 < folderName.length()) {
            String versionString = folderName.substring(index + 2);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < versionString.length(); i++) {
                char c = versionString.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                try {
                    return Integer.parseInt(sb.toString());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return -1;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_TEXT_ALIGNMENT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (context.getProject().getMinSdk() >= 17) {
            return;
        }

        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String gravity = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY);
        String layoutGravity = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY);

        boolean hasGravity = gravity != null && !gravity.isEmpty();
        boolean hasLayoutGravity = layoutGravity != null && !layoutGravity.isEmpty();

        if (!hasGravity && !hasLayoutGravity) {
            context.report(
                COMPAT,
                attribute,
                context.getLocation(attribute),
                "To support older versions than API 17, you must also specify `gravity` or `layout_gravity` when using `textAlignment`"
            );
        }
    }
}
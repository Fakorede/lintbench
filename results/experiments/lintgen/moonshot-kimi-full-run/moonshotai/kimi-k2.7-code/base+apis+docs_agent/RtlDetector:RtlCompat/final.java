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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RtlDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility",
            "API 17 adds a `textAlignment` attribute to specify text alignment. However, "
                    + "if you are supporting older versions than API 17, you must also specify a "
                    + "`gravity` or `layout_gravity` attribute, since older platforms will ignore "
                    + "the `textAlignment` attribute.",
            Category.RTL,
            5,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = new String[] {
            SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
            SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT, SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
            SdkConstants.ATTR_LAYOUT_ALIGN_LEFT, SdkConstants.ATTR_LAYOUT_ALIGN_START,
            SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT, SdkConstants.ATTR_LAYOUT_ALIGN_END,
            SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_START,
            SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_END,
            SdkConstants.ATTR_PADDING_LEFT, SdkConstants.ATTR_PADDING_START,
            SdkConstants.ATTR_PADDING_RIGHT, SdkConstants.ATTR_PADDING_END,
            SdkConstants.ATTR_DRAWABLE_LEFT, SdkConstants.ATTR_DRAWABLE_START,
            SdkConstants.ATTR_DRAWABLE_RIGHT, SdkConstants.ATTR_DRAWABLE_END,
    };

    private static final Collection<String> APPLICABLE_ATTRIBUTES = createApplicableAttributes();

    private static Collection<String> createApplicableAttributes() {
        List<String> attributes = new ArrayList<>(ATTRIBUTES.length + 1);
        attributes.addAll(Arrays.asList(ATTRIBUTES));
        attributes.add(SdkConstants.ATTR_TEXT_ALIGNMENT);
        return attributes;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return APPLICABLE_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String name = attribute.getLocalName();
        if (SdkConstants.ATTR_TEXT_ALIGNMENT.equals(name)) {
            checkTextAlignment(context, attribute);
        } else if (isRtlAttributeName(name)) {
            checkRtlAttribute(context, attribute, name);
        }
    }

    private void checkTextAlignment(XmlContext context, Attr attribute) {
        if (isRtlUnsupported(context)) {
            Element element = attribute.getOwnerElement();
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_GRAVITY)
                    && !element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_GRAVITY)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "When using `textAlignment` on API levels below 17 you must also supply "
                                + "a `gravity` or `layout_gravity` attribute"
                );
            }
        }
    }

    private void checkRtlAttribute(XmlContext context, Attr attribute, String name) {
        if (isRtlUnsupported(context)) {
            String old = convertNewToOld(name);
            if (!old.equals(name)) {
                Element element = attribute.getOwnerElement();
                if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, old)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            String.format(
                                    "To support older versions than API 17 (project specifies %1$s), "
                                            + "you must also specify `%2$s` alongside `%3$s`",
                                    context.getMainProject().getMinSdk(), old, name)
                    );
                }
            }
        }
    }

    private boolean isRtlUnsupported(XmlContext context) {
        if (context.getMainProject().getMinSdk() >= 17) {
            return false;
        }
        File folder = context.file.getParentFile();
        if (folder != null && getFolderVersion(folder) >= 17) {
            return false;
        }
        return true;
    }

    public static boolean isRtlAttributeName(String name) {
        for (int i = 1, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static String convertOldToNew(String name) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return name;
    }

    public static String convertNewToOld(String name) {
        for (int i = 1, n = ATTRIBUTES.length; i < n; i += 2) {
            if (ATTRIBUTES[i].equals(name)) {
                return ATTRIBUTES[i - 1];
            }
        }
        return name;
    }

    public static String convertToOppositeDirection(String name) {
        for (int i = 0, n = ATTRIBUTES.length; i < n; i += 4) {
            if (ATTRIBUTES[i].equals(name)) {
                return ATTRIBUTES[i + 2];
            } else if (ATTRIBUTES[i + 2].equals(name)) {
                return ATTRIBUTES[i];
            } else if (ATTRIBUTES[i + 1].equals(name)) {
                return ATTRIBUTES[i + 3];
            } else if (ATTRIBUTES[i + 3].equals(name)) {
                return ATTRIBUTES[i + 1];
            }
        }
        return name;
    }

    public static int getFolderVersion(File file) {
        String name = file.getName();
        int index = name.indexOf("-v");
        if (index != -1) {
            try {
                return Integer.parseInt(name.substring(index + 2));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1;
    }
}
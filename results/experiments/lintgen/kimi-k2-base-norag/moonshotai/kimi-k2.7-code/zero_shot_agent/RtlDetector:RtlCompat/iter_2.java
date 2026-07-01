package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_DRAWABLE_END;
import static com.android.SdkConstants.ATTR_DRAWABLE_LEFT;
import static com.android.SdkConstants.ATTR_DRAWABLE_RIGHT;
import static com.android.SdkConstants.ATTR_DRAWABLE_START;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_PADDING_END;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_START;
import static com.android.SdkConstants.ATTR_TEXT_ALIGNMENT;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {

    private static final String LEFT = "Left";
    private static final String RIGHT = "Right";
    private static final String START = "Start";
    private static final String END = "End";

    private static final String MESSAGE = "The %1$s attribute was added in API 17. "
            + "When supporting older versions, you must also specify a %2$s attribute.";

    private static final String TEXT_ALIGNMENT_MESSAGE =
            "textAlignment was added in API level 17; when supporting older versions, "
                    + "you must also specify a gravity or layout_gravity attribute";

    public static final Issue ISSUE = Issue.create(
            "RtlCompat",
            "Right-to-left text compatibility issues",
            "The textAlignment attribute was added in API 17. When supporting older versions, "
                    + "you must also specify a gravity or layout_gravity attribute, since older "
                    + "platforms will ignore textAlignment.",
            Category.RTL,
            6,
            Severity.ERROR,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    public static final String[] ATTRIBUTES = new String[] {
            ATTR_LAYOUT_ALIGN_LEFT, ATTR_LAYOUT_ALIGN_START,
            ATTR_LAYOUT_ALIGN_RIGHT, ATTR_LAYOUT_ALIGN_END,
            ATTR_LAYOUT_ALIGN_PARENT_LEFT, ATTR_LAYOUT_ALIGN_PARENT_START,
            ATTR_LAYOUT_ALIGN_PARENT_RIGHT, ATTR_LAYOUT_ALIGN_PARENT_END,
            ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_START,
            ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_END,
            ATTR_PADDING_LEFT, ATTR_PADDING_START,
            ATTR_PADDING_RIGHT, ATTR_PADDING_END,
            ATTR_DRAWABLE_LEFT, ATTR_DRAWABLE_START,
            ATTR_DRAWABLE_RIGHT, ATTR_DRAWABLE_END,
            ATTR_LAYOUT_TO_LEFT_OF, ATTR_LAYOUT_TO_START_OF,
            ATTR_LAYOUT_TO_RIGHT_OF, ATTR_LAYOUT_TO_END_OF,
    };

    private static final String[] NEW_ATTRIBUTES = new String[] {
            ATTR_LAYOUT_ALIGN_START,
            ATTR_LAYOUT_ALIGN_END,
            ATTR_LAYOUT_ALIGN_PARENT_START,
            ATTR_LAYOUT_ALIGN_PARENT_END,
            ATTR_LAYOUT_MARGIN_START,
            ATTR_LAYOUT_MARGIN_END,
            ATTR_PADDING_START,
            ATTR_PADDING_END,
            ATTR_DRAWABLE_START,
            ATTR_DRAWABLE_END,
            ATTR_LAYOUT_TO_START_OF,
            ATTR_LAYOUT_TO_END_OF,
            ATTR_TEXT_ALIGNMENT
    };

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(NEW_ATTRIBUTES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue().trim();
        if (value.isEmpty()) {
            return;
        }

        Project project = context.getMainProject();
        if (project.getMinSdk() >= 17) {
            return;
        }

        File parentFile = context.file.getParentFile();
        if (parentFile != null && getFolderVersion(parentFile) >= 17) {
            return;
        }

        String name = attribute.getLocalName();
        Element element = attribute.getOwnerElement();

        if (ATTR_TEXT_ALIGNMENT.equals(name)) {
            if (!hasGravity(element)) {
                context.report(ISSUE, attribute, context.getValueLocation(attribute),
                        TEXT_ALIGNMENT_MESSAGE);
            }
            return;
        }

        String oldAttribute = convertNewToOld(name);
        if (oldAttribute != null) {
            if (element.getAttributeNS(ANDROID_URI, oldAttribute).trim().isEmpty()) {
                context.report(ISSUE, attribute, context.getValueLocation(attribute),
                        String.format(MESSAGE, name, oldAttribute));
            }
        }
    }

    private static boolean hasGravity(@NonNull Element element) {
        return !element.getAttributeNS(ANDROID_URI, ATTR_GRAVITY).trim().isEmpty()
                || !element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY).trim().isEmpty();
    }

    public static boolean isRtlAttributeName(@NonNull String name) {
        return name.contains(START) || name.contains(END);
    }

    public static String convertOldToNew(@NonNull String oldAttribute) {
        if (oldAttribute.contains(LEFT)) {
            return oldAttribute.replace(LEFT, START);
        } else if (oldAttribute.contains(RIGHT)) {
            return oldAttribute.replace(RIGHT, END);
        }
        return null;
    }

    public static String convertNewToOld(@NonNull String newAttribute) {
        if (newAttribute.contains(START)) {
            return newAttribute.replace(START, LEFT);
        } else if (newAttribute.contains(END)) {
            return newAttribute.replace(END, RIGHT);
        }
        return null;
    }

    public static String convertToOppositeDirection(@NonNull String name) {
        if (name.contains(LEFT)) {
            return name.replace(LEFT, RIGHT);
        } else if (name.contains(RIGHT)) {
            return name.replace(RIGHT, LEFT);
        } else if (name.contains(START)) {
            return name.replace(START, END);
        } else if (name.contains(END)) {
            return name.replace(END, START);
        }
        return null;
    }

    public static int getFolderVersion(@NonNull File file) {
        String name = file.getName();
        int index = name.lastIndexOf("-v");
        if (index == -1 || index + 2 >= name.length()) {
            return -1;
        }

        int end = index + 2;
        while (end < name.length() && Character.isDigit(name.charAt(end))) {
            end++;
        }

        if (end == index + 2) {
            return -1;
        }

        try {
            return Integer.parseInt(name.substring(index + 2, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
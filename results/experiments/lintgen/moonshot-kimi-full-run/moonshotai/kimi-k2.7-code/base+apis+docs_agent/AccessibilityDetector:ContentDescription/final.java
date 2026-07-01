package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

public class AccessibilityDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    private static final String ATTR_HINT = "hint";
    private static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY = "importantForAccessibility";
    private static final String IMAGE_VIEW = "ImageView";
    private static final String IMAGE_BUTTON = "ImageButton";
    private static final String EDIT_TEXT = "EditText";
    private static final String VALUE_NO = "no";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ContentDescription",
                    "Missing contentDescription attribute on image",
                    "Non-textual widgets like ImageViews and ImageButtons should use the "
                            + "`contentDescription` attribute to specify a textual description "
                            + "of the widget so that screen readers and other accessibility "
                            + "tools can adequately describe the user interface.\n"
                            + "\n"
                            + "Note that elements in application screens that are purely "
                            + "decorative and do not provide any content or enable a user "
                            + "action should not have accessibility content descriptions. In "
                            + "this case, set their descriptions to `@null`. If your app's "
                            + "minSdkVersion is 16 or higher, you can instead set these "
                            + "graphical elements' `android:importantForAccessibility` "
                            + "attributes to `no`.\n"
                            + "\n"
                            + "Note that for text fields, you should not set both the `hint` "
                            + "and the `contentDescription` attributes since the hint will never "
                            + "be shown. Just set the `hint`.",
                    Category.A11Y,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(IMAGE_VIEW, IMAGE_BUTTON, EDIT_TEXT);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (IMAGE_VIEW.equals(tag) || IMAGE_BUTTON.equals(tag)) {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                String important =
                        element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
                if (VALUE_NO.equals(important) && context.getMainProject().getMinSdk() >= 16) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Missing `contentDescription` attribute on image");
            }
        } else if (EDIT_TEXT.equals(tag)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)
                    && element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Do not set both `contentDescription` and `hint` on an EditText");
            }
        }
    }
}
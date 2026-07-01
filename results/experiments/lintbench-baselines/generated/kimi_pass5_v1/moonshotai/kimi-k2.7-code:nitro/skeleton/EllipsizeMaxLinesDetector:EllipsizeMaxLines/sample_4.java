package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `android:ellipsize` with `android:maxLines=\"1\"` can cause "
                            + "crashes on some devices. If you need ellipsize, do not use "
                            + "maxLines=\"1\"; use `singleLine=\"true\"` or a larger maxLines "
                            + "value. Otherwise remove the ellipsize attribute.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ELLIPSIZE, ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if (!ATTR_ELLIPSIZE.equals(attribute.getLocalName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (!isTextView(element)) {
            return;
        }

        if ("1".equals(getAttributeValue(element, ATTR_MAX_LINES))) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "Combining `ellipsize` with `maxLines=\"1\"` can cause crashes on some "
                            + "devices; use a larger maxLines value or remove ellipsize");
        }
    }

    private static boolean isTextView(@NonNull Element element) {
        String tag = element.getTagName();
        return tag != null
                && (tag.equals("TextView")
                        || tag.endsWith("TextView")
                        || tag.endsWith(".TextView"));
    }

    @NonNull
    private static String getAttributeValue(@NonNull Element element, @NonNull String localName) {
        Attr attr = element.getAttributeNodeNS(ANDROID_URI, localName);
        if (attr != null) {
            String value = attr.getValue();
            return value == null ? "" : value;
        }
        return "";
    }
}
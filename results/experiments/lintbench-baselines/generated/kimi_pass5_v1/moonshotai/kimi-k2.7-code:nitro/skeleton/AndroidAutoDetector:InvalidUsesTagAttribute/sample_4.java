package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String USES_TAG = "uses";
    private static final String AUTOMOTIVE_APP_TAG = "automotiveApp";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String NAME_ATTR = "name";
    private static final Set<String> VALID_NAMES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "media", "notification", "sms")));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element inside an Android Auto `<automotiveApp>` descriptor "
                            + "must declare a `name` attribute with one of the supported values: "
                            + "`media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_TAG);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide setup is required for this check.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!USES_TAG.equals(element.getTagName())) {
            return;
        }

        Element parent = getParentElement(element);
        if (parent == null || !AUTOMOTIVE_APP_TAG.equals(parent.getTagName())) {
            return;
        }

        Attr attr = element.getAttributeNodeNS(ANDROID_URI, NAME_ATTR);
        if (attr == null) {
            attr = element.getAttributeNode(NAME_ATTR);
        }

        String value = attr != null ? attr.getValue() : null;
        if (value == null || value.isEmpty() || !VALID_NAMES.contains(value)) {
            String message;
            if (value == null || value.isEmpty()) {
                message = "The `<uses>` element must specify a `name` attribute with one of "
                        + "the values: `media`, `notification`, or `sms`.";
            } else {
                message = String.format(
                        "Invalid `name` attribute value \"%s\". Must be one of: "
                                + "`media`, `notification`, or `sms`.", value);
            }
            Location location = attr != null
                    ? context.getValueLocation(attr)
                    : context.getElementLocation(element);
            context.report(ISSUE, location, message);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not used for the InvalidUsesTagAttribute check.
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Not used for the InvalidUsesTagAttribute check.
    }

    private static Element getParentElement(@NonNull Element element) {
        Node parent = element.getParentNode();
        return parent instanceof Element ? (Element) parent : null;
    }
}
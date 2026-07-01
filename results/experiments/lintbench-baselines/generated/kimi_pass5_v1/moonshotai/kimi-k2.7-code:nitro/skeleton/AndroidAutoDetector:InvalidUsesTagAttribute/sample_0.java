package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final Set<String> VALID_NAMES =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList("media", "notification", "sms")));

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element inside an `<automotiveApp>` must specify a valid "
                            + "`android:name` attribute. Allowed values are: `media`, "
                            + "`notification`, and `sms`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        // No project-wide setup is required for this check.
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_USES.equals(element.getTagName())) {
            return;
        }

        Node parent = element.getParentNode();
        if (!(parent instanceof Element)
                || !TAG_AUTOMOTIVE_APP.equals(((Element) parent).getTagName())) {
            return;
        }

        String name = getNameAttribute(element);

        if (name == null || name.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The `<uses>` element must specify an `android:name` attribute.");
            return;
        }

        if (!VALID_NAMES.contains(name)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Invalid value `" + name + "` for `<uses>` `android:name`. "
                            + "Valid values are: `media`, `notification`, and `sms`.");
        }
    }

    private static String getNameAttribute(Element element) {
        String value = element.getAttribute(ATTR_NAME);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        value = element.getAttribute("android:" + ATTR_NAME);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        value = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return null;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // This detector does not inspect Java/UAST classes.
    }

    public void visitMethod(JavaContext context, UMethod method) {
        // This detector does not inspect Java/UAST methods.
    }
}
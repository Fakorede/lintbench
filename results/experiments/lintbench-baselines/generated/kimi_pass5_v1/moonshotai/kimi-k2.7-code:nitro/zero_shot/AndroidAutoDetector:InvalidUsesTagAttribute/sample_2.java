package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetector extends ResourceXmlDetector {

    public static final Issue INVALID_USES_TAG_ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` must have a `name` attribute whose value is one of `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE),
            "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Set<String> VALID_USES_NAMES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("media", "notification", "sms")));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType... folderTypes) {
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType == ResourceFolderType.XML) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null || !TAG_AUTOMOTIVE_APP.equals(parent.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    element,
                    context.getLocation(element),
                    "The `name` attribute is missing or empty for this `<uses>` element. Valid values are `media`, `notification`, or `sms`.");
            return;
        }

        if (!VALID_USES_NAMES.contains(name)) {
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Invalid `name` attribute value `%1$s` for `<uses>`. Valid values are `media`, `notification`, or `sms`.",
                            name));
        }
    }
}
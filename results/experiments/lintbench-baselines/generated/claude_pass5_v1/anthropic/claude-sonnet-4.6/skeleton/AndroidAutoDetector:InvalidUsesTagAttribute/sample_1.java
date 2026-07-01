package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final String VALUE_MEDIA = "media";
    private static final String VALUE_NOTIFICATION = "notification";
    private static final String VALUE_SMS = "sms";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element in `<automotiveApp>` should contain a valid value "
                            + "for the `name` attribute. Valid values are `media`, `notification`, "
                            + "or `sms`.",
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
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Nothing to do here
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check that the parent element is automotiveApp
        if (element.getParentNode() == null) {
            return;
        }

        String parentTagName = element.getParentNode().getNodeName();
        if (!TAG_AUTOMOTIVE_APP.equals(parentTagName)) {
            return;
        }

        // Check the name attribute value
        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            // Missing name attribute
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `name` attribute in `<uses>` element");
            return;
        }

        String nameValue = nameAttr.getValue();
        if (!VALUE_MEDIA.equals(nameValue)
                && !VALUE_NOTIFICATION.equals(nameValue)
                && !VALUE_SMS.equals(nameValue)) {
            context.report(
                    ISSUE,
                    nameAttr,
                    context.getLocation(nameAttr),
                    String.format(
                            "Invalid attribute value `%1$s` for `name` attribute in `<uses>` element. "
                                    + "Valid values are `media`, `notification`, or `sms`.",
                            nameValue));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Nothing to do here for this issue
    }
}
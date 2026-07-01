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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ROOT_TAG = "automotiveApp";
    private static final String USES_TAG = "uses";
    private static final String NAME_ATTR = "name";
    private static final Collection<String> VALID_NAMES =
            Collections.unmodifiableCollection(Arrays.asList("media", "notification", "sms"));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid `name` attribute for `uses` element",
                    "The `<uses>` element inside `<automotiveApp>` must specify an `android:name` "
                            + "attribute with one of the supported values: `media`, `notification`, or `sms`.",
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
        // No project-level setup is required for this check.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element root = context.getDocument().getDocumentElement();
        if (root == null || !ROOT_TAG.equals(root.getTagName())) {
            return;
        }

        String name = getNameAttributeValue(element);
        if (name == null || !VALID_NAMES.contains(name.trim())) {
            String message =
                    "Invalid `name` attribute for `<uses>` element. "
                            + "Valid values are `media`, `notification`, or `sms`.";
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // This issue is specific to automotive metadata XML; no Java source checks are required.
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull org.jetbrains.uast.UMethod method) {
        // This issue is specific to automotive metadata XML; no Java source checks are required.
    }

    private static String getNameAttributeValue(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeType() == Node.ATTRIBUTE_NODE && NAME_ATTR.equals(attr.getLocalName())) {
                return attr.getNodeValue();
            }
        }
        return null;
    }
}
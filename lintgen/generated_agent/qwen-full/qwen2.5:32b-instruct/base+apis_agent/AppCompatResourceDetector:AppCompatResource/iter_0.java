package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AppCompatResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "AppCompatNamespace",
            "When using the appcompat library, menu resources should refer to `showAsAction`, `actionViewClass` or `actionProviderClass` in the `app:` namespace. When not using the appcompat library, use the `android:` namespace.",
            "Using the correct namespace ensures compatibility and proper functionality with the appcompat library.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AppCompatResourceDetector.class, true)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.E_ITEM);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (isUsingAppCompatLibrary(context)) {
            checkAttributeNamespace(context, element, "showAsAction", SdkConstants.ANDROID_URI, "app:");
            checkAttributeNamespace(context, element, "actionViewClass", SdkConstants.ANDROID_URI, "app:");
            checkAttributeNamespace(context, element, "actionProviderClass", SdkConstants.ANDROID_URI, "app:");
        } else {
            checkAttributeNamespace(context, element, "showAsAction", "app:", SdkConstants.ANDROID_URI);
            checkAttributeNamespace(context, element, "actionViewClass", "app:", SdkConstants.ANDROID_URI);
            checkAttributeNamespace(context, element, "actionProviderClass", "app:", SdkConstants.ANDROID_URI);
        }
    }

    private void checkAttributeNamespace(XmlContext context, Element element, String attributeName,
                                         String incorrectNamespace, String correctNamespace) {
        Attr attribute = getAttribute(element, attributeName, incorrectNamespace);
        if (attribute != null) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Use `" + correctNamespace + attributeName + "` instead of `" + incorrectNamespace + attributeName + "`");
        }
    }

    private boolean isUsingAppCompatLibrary(XmlContext context) {
        // This is a placeholder for actual logic to determine if appcompat library is used
        return true; // Assume appcompat is used for simplicity
    }

    private Attr getAttribute(Element element, String name, String namespaceUri) {
        return (Attr) element.getAttributeNodeNS(namespaceUri, name);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MENU.equals(folderType);
    }
}
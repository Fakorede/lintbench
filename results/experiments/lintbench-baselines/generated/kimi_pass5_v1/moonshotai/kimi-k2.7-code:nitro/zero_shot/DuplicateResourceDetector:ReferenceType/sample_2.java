package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be "
                    + "of the same type as the alias.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^[?@](?:[^/:]+:)?([^/]+)/");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String aliasType = element.getAttribute(ATTR_TYPE);
        if (aliasType.isEmpty()) {
            return;
        }

        String reference = XmlUtils.toXmlTextValue(element).trim();
        if (reference.isEmpty()) {
            return;
        }

        String referencedType = getReferencedType(reference);
        if (referencedType == null) {
            return;
        }

        if (!aliasType.equalsIgnoreCase(referencedType)) {
            String name = element.getAttribute(ATTR_NAME);
            String message = String.format(
                    "Expected reference of type '%1$s' for alias '%2$s', but found type '%3$s'",
                    aliasType, name, referencedType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    @Nullable
    private static String getReferencedType(@NonNull String reference) {
        Matcher matcher = REFERENCE_PATTERN.matcher(reference);
        return matcher.find() ? matcher.group(1) : null;
    }
}
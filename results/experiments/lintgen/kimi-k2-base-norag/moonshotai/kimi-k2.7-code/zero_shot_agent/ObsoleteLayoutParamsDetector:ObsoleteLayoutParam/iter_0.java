package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_PREFIX;
... maybe not.

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ObsoleteLayoutParamsDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no "
            + "effect. This usually happens when you change the parent layout or move view "
            + "code around without updating the layout params. This will cause useless "
            + "attribute processing at runtime, and is misleading for others reading the "
            + "layout so the parameter should be removed.",
        Category.CORRECTNESS,
        4,
        Severity.WARNING,
        new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE)
    );

    private static final Map<String, Set<String>> VALID_PARENTS = new HashMap<>();

    static {
        add("layout_weight", "LinearLayout", "TableRow", "RadioGroup", "Toolbar", "SearchView?...");
        ...
    }

    private static void add(String attr, String... parents) {
        VALID_PARENTS.computeIfAbsent(attr, k -> new HashSet<>()).addAll(Arrays.asList(parents));
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableAttributes() {
        return new ArrayList<>(VALID_PARENTS.keySet());
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) return;
        Set<String> validParents = VALID_PARENTS.get(name);
        if (validParents == null) return;

        Element owner = attribute.getOwnerElement();
        Node parentNode = owner.getParentNode();
        if (!(parentNode instanceof Element)) return;
        String parentTag = ((Element) parentNode).getLocalName();
        if (parentTag == null) return;

        if (!validParents.contains(parentTag)) {
            String message = String.format(
                "The `android:%1$s` attribute is not defined for `%2$s` and will not have any effect",
                name, parentTag);
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }
}
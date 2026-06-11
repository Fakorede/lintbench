package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.zip.CentralDirectoryFileHeader;
import com.android.utils.zip.ZipUtil;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

public class DuplicateResourceDetector extends ResourceXmlScanner {

    private static final String ISSUE_ID = "DuplicateResource";
    private static final String SHORT_DESCRIPTION = "Incorrect resource alias type";

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            SHORT_DESCRIPTION,
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return Set.of("name", "item");
    }

    @Nullable
    @Override
    public Void visitAttribute(@NonNull ResourceFolderType folderType,
                               @NonNull ResourceType resourceType,
                               @NonNull Element element,
                               @NonNull Attr attribute) throws IOException, SAXException {

        String attrValue = attribute.getValue();
        if (attrValue.startsWith("@")) {
            Pair<ResourceType, String> referencedResource = XmlUtils.parseReference(attrValue);
            if (referencedResource != null && !resourceType.equals(referencedResource.first)) {
                // Report the issue
                report(
                        element,
                        attribute,
                        "The resource alias type '%s' does not match the referenced resource type '%s'.",
                        resourceType, referencedResource.first
                );
            }
        }

        return super.visitAttribute(folderType, resourceType, element, attribute);
    }
}
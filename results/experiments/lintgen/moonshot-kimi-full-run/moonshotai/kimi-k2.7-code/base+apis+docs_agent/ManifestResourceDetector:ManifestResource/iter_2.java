package com.android.tools.lint.client.api;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Project;
import java.io.File;
import java.util.List;

public class ResourceReference {
    ...
    public static ResourceReference create(@NonNull ResourceUrl url) { ... }
    public boolean isTheme() { ... }
    public List<File> getFiles(@NonNull LintClient client, @NonNull Project project) { ... }
}
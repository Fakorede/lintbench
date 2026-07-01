package androidx.credentials;
public class CredentialManager {
    public static CredentialManager create(Context context) { ... }
    public void createCredential(CreateCredentialRequest request) {}
    public void createCredentialAsync(Activity activity, CreateCredentialRequest request, Executor executor, CredentialManagerCallback callback) {}
}
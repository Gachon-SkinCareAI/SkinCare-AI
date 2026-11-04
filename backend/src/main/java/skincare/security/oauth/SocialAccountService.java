package skincare.security.oauth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import skincare.repository.UserRepository;
import skincare.domain.user.entity.User;
import skincare.domain.user.entity.UserSettings;
import skincare.domain.user.entity.ENUM.Provider;
import skincare.domain.user.entity.ENUM.UserStatus;

@Service
public class SocialAccountService {
    private final UserRepository users;
    private final TransactionTemplate transaction;
    public SocialAccountService(UserRepository users, PlatformTransactionManager manager) {
        this.users = users; this.transaction = new TransactionTemplate(manager);
    }
    User authenticate(SocialIdentity identity) {
        try { return transaction.execute(status -> findOrCreate(identity)); }
        catch (DataIntegrityViolationException race) {
            // Retry in a fresh transaction after a simultaneous first sign-in won the unique key.
            return transaction.execute(status -> active(users.findByProviderAndSocialSubjectHash(identity.provider(), identity.hash()).orElseThrow(() -> race)));
        }
    }
    private User findOrCreate(SocialIdentity identity) {
        var found = users.findByProviderAndSocialSubjectHash(identity.provider(), identity.hash());
        if (found.isPresent()) return active(found.get());
        // Only exact, previously used Google subject IDs can retain a legacy account and its records.
        if (identity.provider() == Provider.GOOGLE && identity.subject().length() <= 29) {
            var legacy = users.findById("google_" + identity.subject());
            if (legacy.isPresent() && legacy.get().getProvider() == Provider.GOOGLE && legacy.get().getSocialSubjectHash() == null) {
                User user = active(legacy.get()); user.attachSocialSubject(identity.hash()); return users.saveAndFlush(user);
            }
        }
        User user = User.builder().userId(java.util.UUID.randomUUID().toString()).provider(identity.provider())
                .socialSubjectHash(identity.hash()).email(identity.email()).nickname(identity.nickname()).profileImageUrl(identity.picture())
                .agreedTerms(false).agreedPrivacy(false).build();
        user.initSettings(UserSettings.builder().user(user).build());
        user.updateLastAccess();
        return users.saveAndFlush(user);
    }
    private User active(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) throw new OAuth2AuthenticationException("account_inactive");
        user.updateLastAccess(); return user;
    }
}

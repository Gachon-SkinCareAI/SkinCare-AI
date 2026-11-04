package skincare.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import skincare.domain.user.entity.*;
import skincare.domain.user.entity.ENUM.UserStatus;
import skincare.repository.*;
import skincare.dto.*;
import java.util.*;

@Service
public class AccountConsentService {
    private final UserRepository users;private final ConsentEventRepository events;private final PublishedPolicies policies;
    public AccountConsentService(UserRepository users,ConsentEventRepository events,
        PublishedPolicies policies) {
        this.users=users;this.events=events;this.policies=policies;
    }
    private User lock(String id) {
        var u=users.lockForAnalysisCreation(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
        if(u.getStatus()!=UserStatus.ACTIVE)throw new ResponseStatusException(UNAUTHORIZED);return u;
    }
    @Transactional public void accept(String id,ConsentRequestDto d) {
        if(!policies.requiredAvailable())throw new ResponseStatusException(CONFLICT,"필수 정책 문서가 연결되지 않아 아직 동의할 수 없습니다.");
        ConsentPolicy.validate(d.isAgreedTerms(),d.isAgreedPrivacy(),d.getTermsVersion(),d.getPrivacyVersion());
        if(!d.isAgreedHealthData()||!ConsentPolicy.HEALTH_VERSION.equals(d.getHealthDataVersion()))
            throw new ResponseStatusException(BAD_REQUEST,"현재 버전의 건강정보 처리 동의를 별도로 확인해 주세요.");
        var u=lock(id);
        if(!u.isAgreedTerms()||!ConsentPolicy.VERSION.equals(u.getTermsVersion()))event(id,"TERMS",ConsentPolicy.VERSION,true);
        if(!u.isAgreedPrivacy()||!ConsentPolicy.VERSION.equals(u.getPrivacyVersion()))event(id,"PRIVACY",ConsentPolicy.VERSION,true);
        if(!ConsentPolicy.HEALTH_VERSION.equals(u.getHealthConsentVersion()))event(id,"HEALTH_DATA",ConsentPolicy.HEALTH_VERSION,true);
        if(!u.hasRequiredConsents()){u.acceptConsents(d.getTermsVersion(),d.getPrivacyVersion());u.acceptHealthConsent(d.getHealthDataVersion());}
    }
    public record TrainingRequest(Boolean granted,String version) {}
    @Transactional public void training(String id,TrainingRequest d) {
        if(d.granted()==null||!ConsentPolicy.TRAINING_VERSION.equals(d.version()))throw new ResponseStatusException(BAD_REQUEST,"동의 여부와 현재 정책 버전이 필요합니다.");
        if(d.granted()&&!trainingEnabled())throw new ResponseStatusException(CONFLICT,"학습 목적 안내 문서가 연결된 후 선택 동의할 수 있습니다.");
        var u=lock(id);
        if(u.isTrainingGranted()==d.granted()&&Objects.equals(u.getTrainingConsentVersion(),d.version()))return;
        u.trainingConsent(d.granted(),d.version());event(id,"MODEL_TRAINING",d.version(),d.granted());
    }
    private boolean trainingEnabled(){return policies.trainingUrl()!=null;}
    private void event(String id,String purpose,String version,boolean granted){events.save(new ConsentEvent(id,purpose,version,granted));}
    @Transactional(readOnly=true) public Map<String,Object> state(String id,int page,int size) {
        var u=users.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("healthDataVersion",u.getHealthConsentVersion());data.put("healthConsentedAt",u.getHealthConsentedAt());
        data.put("trainingGranted",u.isTrainingGranted()&&ConsentPolicy.TRAINING_VERSION.equals(u.getTrainingConsentVersion()));
        data.put("trainingVersion",ConsentPolicy.TRAINING_VERSION);data.put("trainingUpdatedAt",u.getTrainingConsentedAt());
        data.put("trainingPolicyUrl",policies.trainingUrl());data.put("trainingAvailable",trainingEnabled());
        data.put("trainingExportEnabled",false); // No consent alone grants a training/data-export capability.
        data.put("history",ServicePage.from(events.findByUserId(id,ServiceInput.page(page,size,"occurredAt","id"))));return data;
    }
}

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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final UserRepository users;private final ConsentEventRepository events;private final PublishedPolicies policies;
    public AccountConsentService(UserRepository users,ConsentEventRepository events,
        PublishedPolicies policies,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc=jdbc;this.users=users;this.events=events;this.policies=policies;
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
        if(d.getAgreedLocation()!=null)location(id,new LocationRequest(d.getAgreedLocation(),d.getLocationVersion()));
    }
    public record TrainingRequest(Boolean granted,String version) {}
    @Transactional public void training(String id,TrainingRequest d) {
        if(d.granted()==null||!ConsentPolicy.TRAINING_VERSION.equals(d.version()))throw new ResponseStatusException(BAD_REQUEST,"동의 여부와 현재 정책 버전이 필요합니다.");
        var u=lock(id);
        if(d.granted()&&u.getProvider()!=skincare.domain.user.entity.ENUM.Provider.GUEST&&!trainingEnabled())
            throw new ResponseStatusException(CONFLICT,"학습 목적 안내 문서가 연결된 후 선택 동의할 수 있습니다.");
        if(u.isTrainingGranted()==d.granted()&&Objects.equals(u.getTrainingConsentVersion(),d.version()))return;
        u.trainingConsent(d.granted(),d.version());event(id,"MODEL_TRAINING",d.version(),d.granted());
        if(!d.granted())jdbc.update("UPDATE analysis_sessions SET training_eligible=false,training_review_status='WITHDRAWN' WHERE user_id=? AND training_eligible=true",id);
    }
    public record LocationRequest(Boolean granted,String version) {}
    @Transactional public void location(String id,LocationRequest d) {
        if(d==null||d.granted()==null||!ConsentPolicy.LOCATION_VERSION.equals(d.version()))throw new ResponseStatusException(BAD_REQUEST,"현재 위치정보 동의 버전이 필요합니다.");
        var u=lock(id);
        if(u.getProvider()==skincare.domain.user.entity.ENUM.Provider.GUEST)throw new ResponseStatusException(FORBIDDEN,"위치 기반 검색은 가입 후 선택 동의해 주세요.");
        if(u.isLocationGranted()!=d.granted()||!Objects.equals(u.getLocationConsentVersion(),d.version())) {
            u.locationConsent(d.granted(),d.version());event(id,"LOCATION",d.version(),d.granted());
        }
    }
    @Transactional(readOnly=true) public void requireLocation(String id) {
        var u=users.findById(id).orElseThrow(()->new ResponseStatusException(UNAUTHORIZED));
        if(!u.hasLocationConsent())throw new ResponseStatusException(FORBIDDEN,"주변 의료기관을 검색하려면 위치정보 이용에 선택 동의해 주세요.");
    }
    private boolean trainingEnabled(){return policies.trainingUrl()!=null;}
    private void event(String id,String purpose,String version,boolean granted){events.save(new ConsentEvent(id,purpose,version,granted));}
    @Transactional(readOnly=true) public Map<String,Object> state(String id,int page,int size) {
        var u=users.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("healthDataVersion",u.getHealthConsentVersion());data.put("healthConsentedAt",u.getHealthConsentedAt());
        data.put("trainingGranted",u.isTrainingGranted()&&ConsentPolicy.TRAINING_VERSION.equals(u.getTrainingConsentVersion()));
        data.put("trainingVersion",ConsentPolicy.TRAINING_VERSION);data.put("trainingUpdatedAt",u.getTrainingConsentedAt());
        boolean guest=u.getProvider()==skincare.domain.user.entity.ENUM.Provider.GUEST;
        data.put("trainingPolicyUrl",guest?"/api/wc/policies/2026-11-04/training":policies.trainingUrl());data.put("trainingAvailable",guest||trainingEnabled());
        data.put("locationGranted",u.hasLocationConsent());data.put("locationVersion",ConsentPolicy.LOCATION_VERSION);
        data.put("locationUpdatedAt",u.getLocationConsentedAt());data.put("locationPolicyUrl","/api/wc/policies/2026-11-04/location");
        data.put("trainingExportEnabled",false); // No consent alone grants a training/data-export capability.
        data.put("history",ServicePage.from(events.findByUserId(id,ServiceInput.page(page,size,"occurredAt","id"))));return data;
    }
}

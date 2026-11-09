package skincare.service;

import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;

import skincare.repository.AdminRepository;

import java.time.Instant;
import java.util.*;

@Service
// ServiceReadiness 비즈니스 로직 처리
public class ServiceReadiness {
    public record Check(String id, String label, String status, String nextAction) {}

    public record Report(
            Instant checkedAt,
            boolean externalConnectionsVerified,
            boolean releaseApproved,
            List<Check> checks) {}

    private final Environment env;
    private final ClientRegistrationRepository clients;
    private final PublishedPolicies policies;
    private final AdminRepository admins;

    public ServiceReadiness(
            Environment env,
            ClientRegistrationRepository clients,
            PublishedPolicies policies,
            AdminRepository admins) {
        this.env = env;
        this.clients = clients;
        this.policies = policies;
        this.admins = admins;
    }

    private String value(String name) {
        return env.getProperty(name, "").strip();
    }

    private Check document(String id, String label, String property, String url) {
        String state =
                url != null ? "CONFIGURED" : value(property).isEmpty() ? "MISSING" : "INVALID";
        return new Check(id, label, state, "검토된 공개 HTTPS 문서와 서버 버전을 맞춘 뒤 실제 링크를 확인하세요.");
    }

    public Report report() {
        var checks = new ArrayList<Check>();
        for (String id : List.of("google", "kakao", "naver")) {
            boolean registered = clients.findByRegistrationId(id) != null;
            String status =
                    registered
                            ? "CONFIGURED"
                            : (!value("social." + id + ".client-id").isEmpty()
                                            || !value("social." + id + ".client-secret").isEmpty())
                                    ? "INCOMPLETE"
                                    : "MISSING";
            checks.add(
                    new Check(
                            id,
                            id + " 소셜 로그인",
                            status,
                            "제공자 앱 등록·키·콜백 주소를 설정하고 실제 계정과 기기에서 확인하세요."));
        }
        var m = policies.manifest();
        checks.add(document("terms", "이용약관", "skincare.consent.terms-policy-url", m.terms().url()));
        checks.add(
                document(
                        "privacy",
                        "개인정보·건강정보 안내",
                        "skincare.consent.privacy-policy-url",
                        m.privacy().url()));
        checks.add(
                document(
                        "training",
                        "선택 학습 목적 안내",
                        "skincare.consent.training-policy-url",
                        m.training().url()));
        checks.add(
                new Check(
                        "medical",
                        "공공 의료기관 검색",
                        value("skincare.medical.service-key").isEmpty() ? "MISSING" : "CONFIGURED",
                        "병·의원과 약국 서비스 모두 활용신청 후 실제 응답을 확인하세요."));
        var reviewers =
                Arrays.stream(value("skincare.review.clinical-reviewers").split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();
        String status =
                reviewers.isEmpty()
                        ? "MISSING"
                        : reviewers.stream().allMatch(admins::existsById)
                                ? "CONFIGURED"
                                : "INVALID";
        checks.add(
                new Check(
                        "reviewers",
                        "임상 검수 담당자",
                        status,
                        "실제 담당자의 자격·역할과 관리자 계정을 확인한 뒤 지정하세요. 지정만으로 검수가 완료되지는 않습니다."));
        checks.add(
                new Check(
                        "training-export",
                        "학습 데이터 반출",
                        "DISABLED",
                        "사용자 사진의 자동 반출·학습은 비활성화되어 있습니다."));
        checks.add(
                new Check(
                        "clinical-release",
                        "임상 서비스 승인",
                        "PENDING_REVIEW",
                        "전문가 검수와 운영 정책 검토 전까지 연구·시연 범위를 유지하세요."));
        return new Report(Instant.now(), false, false, checks);
    }
}

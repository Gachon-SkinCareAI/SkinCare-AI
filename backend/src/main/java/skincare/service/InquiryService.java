package skincare.service;

import static org.springframework.http.HttpStatus.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import skincare.domain.inquiry.entity.*;
import skincare.domain.inquiry.entity.ENUM.*;
import skincare.dto.*;
import skincare.repository.*;

import java.util.*;

@Service
@RequiredArgsConstructor
// InquiryService 비즈니스 로직 처리
public class InquiryService {
    private final InquiryRepository inquiries;
    private final AdminAuditService audit;
    private final jakarta.persistence.EntityManager entityManager;
    private final InquiryAnswerRepository answers;
    private final UserRepository users;
    private final AnalysisSessionRepository analyses;

    @Transactional
    public InquiryView create(String owner, InquiryRequest dto) {
        String request = ServiceInput.uuid(dto.clientRequestId()),
                title = ServiceInput.text(dto.title(), 200, "제목"),
                content = ServiceInput.text(dto.content(), 5000, "문의 내용");
        if (dto.type() == null) throw new ResponseStatusException(BAD_REQUEST, "문의 유형을 선택해 주세요.");
        String analysis = ServiceInput.optional(dto.analysisId(), 36, "분석 ID");
        var user =
                users.lockForAnalysisCreation(owner)
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var replay = inquiries.findByUser_UserIdAndClientRequestId(owner, request);
        if (replay.isPresent()) {
            var i = replay.get();
            if (!i.getTitle().equals(title)
                    || !i.getContent().equals(content)
                    || i.getInquiryType() != dto.type()
                    || !Objects.equals(i.getAnalysisId(), analysis))
                throw new ResponseStatusException(CONFLICT, "같은 요청 ID에 다른 문의를 보낼 수 없습니다.");
            return view(i, true);
        }
        if (analysis != null && analyses.findByIdAndUserId(analysis, owner).isEmpty())
            throw new ResponseStatusException(NOT_FOUND, "연결할 분석 기록을 찾을 수 없습니다.");
        var i =
                Inquiry.builder()
                        .inquiryId(UUID.randomUUID().toString())
                        .user(user)
                        .clientRequestId(request)
                        .inquiryType(dto.type())
                        .title(title)
                        .content(content)
                        .analysisId(analysis)
                        .build();
        return view(inquiries.saveAndFlush(i), true);
    }

    @Transactional(readOnly = true)
    public ServicePage<InquiryView> list(String owner, int page, int size) {
        return ServicePage.from(
                inquiries
                        .findByUser_UserId(
                                owner, ServiceInput.page(page, size, "createdAt", "inquiryId"))
                        .map(i -> view(i, false)));
    }

    @Transactional(readOnly = true)
    public InquiryView get(String owner, String id) {
        return view(
                inquiries
                        .findByInquiryIdAndUser_UserId(id, owner)
                        .orElseThrow(
                                () -> new ResponseStatusException(NOT_FOUND, "문의를 찾을 수 없습니다.")),
                true);
    }

    @Transactional(readOnly = true)
    public ServicePage<InquiryView> listAdmin(InquiryStatus status, int page, int size) {
        return ServicePage.from(
                inquiries
                        .listAdmin(status, ServiceInput.page(page, size, "createdAt", "inquiryId"))
                        .map(i -> view(i, false)));
    }

    @Transactional
    public InquiryView getAdmin(String id) {
        audit.current("INQUIRY_READ", id, null);
        return view(
                inquiries
                        .findById(id)
                        .orElseThrow(
                                () -> new ResponseStatusException(NOT_FOUND, "문의를 찾을 수 없습니다.")),
                true);
    }

    @Transactional
    public InquiryView reply(String admin, String id, InquiryReplyRequest dto) {
        String request = ServiceInput.uuid(dto.clientRequestId()),
                content = ServiceInput.text(dto.content(), 5000, "답변");
        var i =
                inquiries
                        .lockById(id)
                        .orElseThrow(
                                () -> new ResponseStatusException(NOT_FOUND, "문의를 찾을 수 없습니다."));
        var existing = answers.findByInquiry_InquiryId(id);
        if (existing.isPresent()) {
            var answer = existing.get();
            if (answer.getAnswerId().equals(request)
                    && answer.getAnswerContent().equals(content)
                    && Objects.equals(answer.getAdminId(), admin)) return view(i, true);
            throw new ResponseStatusException(CONFLICT, "이미 답변이 등록된 문의입니다. 새로고침해 주세요.");
        }
        if (dto.expectedVersion() == null || dto.expectedVersion() != i.getVersion())
            throw new ResponseStatusException(CONFLICT, "문의 상태가 바뀌었습니다. 새로고침해 주세요.");
        if (answers.existsById(request))
            throw new ResponseStatusException(CONFLICT, "다른 답변에서 사용한 요청 ID입니다.");
        entityManager.persist(
                InquiryAnswer.builder()
                        .answerId(request)
                        .inquiry(i)
                        .adminId(admin)
                        .answerContent(content)
                        .build());
        entityManager.flush();
        i.completeAnswer();
        inquiries.flush();
        audit.record(admin, "INQUIRY_REPLY", id, "COMPLETED");
        return view(i, true);
    }

    private InquiryView view(Inquiry i, boolean detail) {
        var reply =
                detail
                        ? answers.findByInquiry_InquiryId(i.getInquiryId())
                                .map(
                                        a ->
                                                new InquiryView.Reply(
                                                        a.getAnswerContent(), a.getCreatedAt()))
                                .orElse(null)
                        : null;
        return new InquiryView(
                i.getInquiryId(),
                i.getInquiryType().name(),
                i.getTitle(),
                i.getStatus().name(),
                i.getAnalysisId(),
                i.getCreatedAt(),
                i.getVersion(),
                detail ? i.getContent() : null,
                reply);
    }
}

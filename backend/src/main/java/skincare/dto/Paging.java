package skincare.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// 목록 조회 API의 page/size 파라미터를 Spring Data Pageable로 변환하는 유틸리티
// page는 1부터 시작하며, 과도한 조회를 막기 위해 size는 최대 100으로 제한
// Paging 요청과 응답 데이터
public final class Paging {

    private static final int MAX_SIZE = 100;

    private Paging() {}

    // 1부터 시작하는 page 번호를 0부터 시작하는 Pageable로 변환
    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(page, 1) - 1, clampSize(size));
    }

    // 정렬 조건을 포함한 Pageable 변환
    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 1) - 1, clampSize(size), sort);
    }

    // size를 1 ~ MAX_SIZE 범위로 보정
    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}

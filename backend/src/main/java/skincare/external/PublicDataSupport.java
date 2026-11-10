package skincare.external;

import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

import skincare.external.DirectoryException.Failure;
import skincare.external.DirectoryException.Source;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.LongConsumer;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 공공데이터포털(data.go.kr) 목록 API를 전부 내려받는 수집기들의 공통 부분: HTTP 호출, 안전한 XML 파싱, 오류코드 변환, 페이지 병렬 수집,
 * 재시도, 부분 수집 거부.
 */
final class PublicDataSupport {
    private PublicDataSupport() {}

    static final int MAX_PAGES = 100; // 응답 이상 시 무한 반복 방지
    static final int ATTEMPTS = 3;
    static final double COMPLETENESS = 0.99;
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    record Page<T>(long total, List<T> items) {}

    @FunctionalInterface
    interface PageLoader<T> {
        Page<T> load(int page) throws IOException;
    }

    /** 재시도 대상 호출 (페이지 번호는 이미 정해진 상태). */
    @FunctionalInterface
    interface Call<T> {
        Page<T> run() throws IOException;
    }

    // ---- HTTP ----

    /** 키·URL·응답 본문을 로그에 남기지 않고 상태 코드를 실패 원인으로 바꾼다. */
    static byte[] httpGet(URI uri, int readTimeoutMillis, Source source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/xml");
            int code = connection.getResponseCode();
            if (code == 401 || code == 403) throw new DirectoryException(Failure.AUTH_ERROR, source);
            if (code == 429) throw new DirectoryException(Failure.RATE_LIMITED, source);
            if (code != 200) throw new DirectoryException(Failure.UNAVAILABLE, source);
            try (var in = connection.getInputStream()) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new DirectoryException(Failure.INVALID_RESPONSE, source);
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    // ---- XML ----

    /** 응답 XML의 루트를 돌려준다. 포털 오류코드는 실패 원인으로 변환하고, 정상(00)이 아니면 거부한다. */
    static Element parseRoot(byte[] bytes, Source source) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            String code = text(root, "resultCode");
            if (code == null) code = text(root, "returnReasonCode");
            if (Set.of("20", "21", "30", "31", "32").contains(Objects.toString(code, "")))
                throw new DirectoryException(Failure.AUTH_ERROR, source);
            if (Set.of("22", "23").contains(Objects.toString(code, "")))
                throw new DirectoryException(Failure.RATE_LIMITED, source);
            if (!"00".equals(code)) throw new DirectoryException(Failure.INVALID_RESPONSE, source);
            return root;
        } catch (DirectoryException known) {
            throw known;
        } catch (Exception malformed) {
            throw new DirectoryException(Failure.INVALID_RESPONSE, source);
        }
    }

    static long totalCount(Element root, Source source) {
        try {
            long total = Long.parseLong(Objects.requireNonNull(text(root, "totalCount")));
            if (total < 0) throw new IllegalArgumentException();
            return total;
        } catch (RuntimeException invalid) {
            throw new DirectoryException(Failure.INVALID_RESPONSE, source);
        }
    }

    static String text(Element e, String tag) {
        var node = e.getElementsByTagName(tag).item(0);
        if (node == null) return null;
        String value = node.getTextContent().strip();
        if (value.length() > 1000) throw new IllegalArgumentException();
        return value.isEmpty() ? null : value;
    }

    /** 연속 공백을 하나로 줄이고 앞뒤를 자른다 (공공데이터에는 "  경희당한약국 "처럼 공백이 섞여 있다). */
    static String collapse(String value) {
        if (value == null) return null;
        String collapsed = value.replaceAll("\\s+", " ").strip();
        return collapsed.isEmpty() ? null : collapsed;
    }

    static Double coordinate(String value, int bound) {
        try {
            double v = Double.parseDouble(value);
            return Double.isFinite(v) && v != 0 && Math.abs(v) <= bound ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---- 전체 수집 ----

    /**
     * 모든 페이지를 내려받는다. 첫 페이지로 전체 건수를 알아낸 뒤 나머지를 제한된 병렬도로 받는다. 페이지마다 재시도하고, 고유 항목이
     * 전체 건수의 99% 미만이면 부분 결과로 보고 거부한다.
     */
    static <T> List<T> fetchAll(
            Source source,
            int pageSize,
            int concurrency,
            PageLoader<T> loader,
            Function<T, String> idOf,
            LongConsumer pause) {
        Page<T> first = withRetry(source, () -> loader.load(1), pause);
        long total = first.total();
        int pages = (int) Math.min(MAX_PAGES, (total + pageSize - 1) / pageSize);
        List<Page<T>> results = new ArrayList<>();
        results.add(first);
        if (pages > 1) {
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, pages - 1));
            try {
                List<Future<Page<T>>> futures = new ArrayList<>();
                for (int p = 2; p <= pages; p++) {
                    int page = p;
                    futures.add(pool.submit(() -> withRetry(source, () -> loader.load(page), pause)));
                }
                for (var future : futures) results.add(await(future, source));
            } finally {
                pool.shutdownNow();
            }
        }
        Map<String, T> byId = new LinkedHashMap<>();
        for (var page : results) for (T item : page.items()) byId.putIfAbsent(idOf.apply(item), item);
        // 페이지 경계에서 누락이 생겼거나 일부 항목이 거부된 경우: 부분 결과로 동기화하지 않는다
        if (byId.size() < total * COMPLETENESS) throw new DirectoryException(Failure.INCOMPLETE, source);
        return new ArrayList<>(byId.values());
    }

    private static <T> Page<T> await(Future<Page<T>> future, Source source) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DirectoryException known) throw known;
            throw new DirectoryException(Failure.UNAVAILABLE, source);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DirectoryException(Failure.UNAVAILABLE, source);
        }
    }

    static <T> Page<T> withRetry(Source source, Call<T> call, LongConsumer pause) {
        DirectoryException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return call.run();
            } catch (DirectoryException e) {
                last = e;
                // 키 오류는 재시도해도 같다
                if (e.failure() == Failure.AUTH_ERROR || e.failure() == Failure.UNCONFIGURED) throw e;
            } catch (IOException e) { // 로그에 URL·키가 남지 않도록 원인 메시지는 버린다
                last = new DirectoryException(Failure.UNAVAILABLE, source);
            }
            if (attempt < ATTEMPTS) pause.accept(attempt * 1000L);
        }
        throw last;
    }
}

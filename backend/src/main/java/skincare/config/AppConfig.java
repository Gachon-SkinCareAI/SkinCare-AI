package skincare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

// 애플리케이션 공통 Bean 설정 클래스
@Configuration
// AppConfig 애플리케이션 설정
public class AppConfig {

    // 외부 서버 연결 제한 시간 (ms)
    @Value("${ai.server.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    // 외부 서버 응답 대기 제한 시간 (ms)
    @Value("${ai.server.read-timeout-ms:30000}")
    private int readTimeoutMs;

    // 외부 서버(공공데이터 OpenAPI 등) HTTP 통신용 RestTemplate 빈 등록
    // 응답 지연 시 요청 스레드가 무한 대기하지 않도록 연결/응답 타임아웃 설정
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate(createRequestFactory());
    }

    // AI 서버(FastAPI) 전용 RestTemplate 빈 등록
    // AI 서버는 snake_case JSON을 사용하므로 요청/응답 DTO를 자동으로 snake_case ↔ camelCase 변환하고,
    // AI 서버 응답에 새 필드가 추가되어도 역직렬화가 실패하지 않도록 알 수 없는 필드는 무시
    @Bean
    public RestTemplate aiRestTemplate() {
        JsonMapper aiJsonMapper =
                JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();

        AllEncompassingFormHttpMessageConverter formConverter =
                new AllEncompassingFormHttpMessageConverter();
        RestTemplate restTemplate = new RestTemplate(createRequestFactory());
        restTemplate.setMessageConverters(
                List.of(
                        new ByteArrayHttpMessageConverter(),
                        new StringHttpMessageConverter(),
                        new ResourceHttpMessageConverter(),
                        formConverter,
                        new JacksonJsonHttpMessageConverter(aiJsonMapper)));
        return restTemplate;
    }

    // 연결/응답 타임아웃이 적용된 HTTP 요청 팩토리 생성
    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return requestFactory;
    }
}

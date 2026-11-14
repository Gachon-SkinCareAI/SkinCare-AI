package skincare.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
// AdminPageController API 요청 처리
public class AdminPageController {

    @GetMapping({
        "/admin",
        "/admin/",
        "/admin/dashboard",
        "/admin/diagnoses",
        "/admin/monitoring",
        "/admin/facilities",
        "/admin/users",
        "/admin/inquiries",
        "/admin/analyses"
    })
    public String page() {
        return "forward:/admin/index.html";
    }

    // 운영 도구(콘텐츠 검수·감사 이력·비밀번호 변경). index.html이 ./app.js 같은
    // 상대 경로를 쓰므로 끝에 / 가 붙은 주소에서만 열리게 한다.
    @GetMapping("/admin/ops")
    public String opsWithoutSlash() {
        return "redirect:/admin/ops/";
    }

    @GetMapping("/admin/ops/")
    public String ops() {
        return "forward:/admin/ops/index.html";
    }
}

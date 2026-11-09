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
}

import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import skincare.SkincareApplication;
import skincare.domain.user.entity.User;
import skincare.repository.UserRepository;
import skincare.service.ConsentPolicy;

/** Local lifecycle helper. Creates no users and never bypasses social authentication. */
public class DevelopmentLauncher {
    public static void main(String[] args) {
        var context = SpringApplication.run(SkincareApplication.class, args);
        new Thread(() -> {
            try { while (!java.nio.file.Files.exists(java.nio.file.Path.of(System.getenv("LOCAL_STOP_PATH")))) Thread.sleep(300); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { context.close(); }
        }, "local-graceful-stop").start();
        System.out.println("SOCIAL_AUTH_APP_READY");
    }
}

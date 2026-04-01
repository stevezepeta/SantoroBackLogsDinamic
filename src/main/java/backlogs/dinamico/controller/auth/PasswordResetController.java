package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.ForgotPasswordRequest;
import backlogs.dinamico.api.dto.ResetPasswordRequest;
import backlogs.dinamico.service.auth.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
public class PasswordResetController {

    private final PasswordResetService service;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgot(@RequestBody ForgotPasswordRequest body) {

        long start = System.currentTimeMillis();
        log.info("[POST] /api/auth/forgot-password email={}", body.getEmail());

        service.requestReset(body);

        ApiResponse<Void> resp =
                ApiResponse.ok("ok","reset_email_sent", null);
        log.info("[POST] /api/auth/forgot-password done in {} ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok(resp);

    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> reset(@RequestBody ResetPasswordRequest body) {
        long start = System.currentTimeMillis();
        log.info("[POST] /api/auth/reset-password");

        service.resetPassword(body);

        ApiResponse<Void> resp =
                ApiResponse.ok("ok", "password_reset_success", null);
        log.info("[POST] /api/auth/reset-password done in {} ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok(resp);
    }

}

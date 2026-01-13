package nl.ak.skillswap.skillswap;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "sub", jwt.getSubject(),
                "email", jwt.getClaim("email"),
                "preferred_username", jwt.getClaim("preferred_username"),
                "realm_access", jwt.getClaim("realm_access")
        );
    }
}

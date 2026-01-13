package nl.ak.skillswap.userservice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.api.dto.UpdatePreferencesRequest;
import nl.ak.skillswap.userservice.api.dto.UpdateProfileRequest;
import nl.ak.skillswap.userservice.api.dto.UserDto;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.service.UserService;
import nl.ak.skillswap.userservice.support.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        User u = sync(authentication);
        return UserDto.from(u);
    }

    @PutMapping("/me/profile")
    public UserDto updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest req) {
        User me = sync(authentication);
        User updated = userService.updateProfile(me.getId(), req.displayName(), req.timeZone(), req.bio());
        return UserDto.from(updated);
    }

    @PutMapping("/me/preferences")
    public UserDto updatePreferences(Authentication authentication, @Valid @RequestBody UpdatePreferencesRequest req) {
        User me = sync(authentication);
        User updated = userService.updatePreferences(me.getId(), req.allowMatching(), req.allowEmails());
        return UserDto.from(updated);
    }

    @DeleteMapping("/me")
    public void deleteMe(Authentication authentication) {
        User me = sync(authentication);
        userService.softDeleteAccount(me.getId());
    }

    private User sync(Authentication authentication) {
        String sub = CurrentUser.externalId(authentication);
        String email = CurrentUser.email(authentication);
        String username = CurrentUser.preferredUsername(authentication);
        return userService.syncFromKeycloak(sub, email, username != null ? username : "User");
    }
}

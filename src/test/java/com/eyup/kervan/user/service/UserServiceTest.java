package com.eyup.kervan.user.service;

import com.eyup.kervan.user.dto.UpdateUserRequest;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.entity.User;
import com.eyup.kervan.user.exception.EmailAlreadyExistsException;
import com.eyup.kervan.user.exception.UserNotFoundException;
import com.eyup.kervan.user.mapper.UserMapper;
import com.eyup.kervan.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create("  Ali@Example.COM ", "secret123", " Ali Veli ", Role.USER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("ali@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getFullName()).isEqualTo("Ali Veli");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create("ali@example.com", "secret123", "Ali Veli", Role.USER))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void emailNormalizationIgnoresTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(UserService.normalizeEmail("ISTANBUL@Example.com")).isEqualTo("istanbul@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void getByIdThrowsWhenUserIsMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfileChangesFullName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User("a@b.com", "h", "Old", Role.USER)));

        userService.updateProfile(1L, new UpdateUserRequest(" New Name "));

        verify(userMapper).toResponse(argThat(u -> u.getFullName().equals("New Name")));
    }
}

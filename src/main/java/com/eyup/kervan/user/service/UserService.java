package com.eyup.kervan.user.service;

import com.eyup.kervan.common.dto.PageResponse;
import com.eyup.kervan.common.exception.InvalidSortException;
import com.eyup.kervan.user.dto.UpdateUserRequest;
import com.eyup.kervan.user.dto.UserResponse;
import com.eyup.kervan.user.entity.Role;
import com.eyup.kervan.user.entity.User;
import com.eyup.kervan.user.exception.EmailAlreadyExistsException;
import com.eyup.kervan.user.exception.UserNotFoundException;
import com.eyup.kervan.user.mapper.UserMapper;
import com.eyup.kervan.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    // whitelist: blocks sorting by sensitive (passwordHash) or unknown fields, which would otherwise be a 500
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "email", "fullName", "createdAt");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(String email, String rawPassword, String fullName, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim(), role);
        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse getById(Long id) {
        return userMapper.toResponse(findUser(id));
    }

    public PageResponse<UserResponse> list(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        }
        return PageResponse.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @Transactional
    public UserResponse updateProfile(Long id, UpdateUserRequest request) {
        User user = findUser(id);
        // no save() needed, dirty checking flushes the change at commit
        user.changeFullName(request.fullName().trim());
        return userMapper.toResponse(user);
    }

    public boolean adminExists() {
        return userRepository.existsByRole(Role.ADMIN);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    // Locale.ROOT so that on a Turkish-locale JVM "I" does not become dotless "ı"
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

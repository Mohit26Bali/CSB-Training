package com.icc.assignment.service;

import com.icc.assignment.dto.UserResponse;
import com.icc.assignment.dto.UserSummaryResponse;
import com.icc.assignment.entity.AppUser;
import com.icc.assignment.mapper.UserMapper;
import com.icc.assignment.repository.AppUserRepository;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, new UserMapper());
    }

    @Test
    void getMe_returnsTheCallersOwnRecord() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(
                new AppUser(2L, "sd.engineer", "hash", "Sam", "sam@icc.local", Role.SUPPORT_ENGINEER, 1L, true)));

        UserResponse me = service.getMe(new JwtClaims(2L, "sd.engineer", Role.SUPPORT_ENGINEER, 1L));

        assertEquals("sam@icc.local", me.email());
        assertEquals(1L, me.teamId());
    }

    @Test
    void getMe_whenTheUserNoLongerExists_isNotFound() {
        when(userRepository.findById(8L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.getMe(new JwtClaims(8L, "ghost", Role.EMPLOYEE, null)));
    }

    @Test
    void listUsers_returnsOnlyTheBasicFields() {
        when(userRepository.findAll(Sort.by("id"))).thenReturn(List.of(
                new AppUser(1L, "admin", "hash", "Alex", "a@icc.local", Role.ADMIN, null, true)));

        List<UserSummaryResponse> users = service.listUsers();

        assertEquals(1, users.size());
        assertEquals("Alex", users.get(0).fullName());
        assertEquals(Role.ADMIN, users.get(0).role());
    }
}

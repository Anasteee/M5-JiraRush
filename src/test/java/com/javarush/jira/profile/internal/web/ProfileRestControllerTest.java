package com.javarush.jira.profile.internal.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.http.MediaType;  // For MediaType.APPLICATION_JSON
import com.javarush.jira.common.error.ErrorMessageHandler;
import com.javarush.jira.common.error.IllegalRequestDataException;
import com.javarush.jira.common.util.validation.ValidationUtil;
import com.javarush.jira.login.AuthUser;
import com.javarush.jira.login.User;
import com.javarush.jira.profile.ProfileTo;
import com.javarush.jira.profile.internal.ProfileMapper;
import com.javarush.jira.profile.internal.ProfileRepository;
import com.javarush.jira.profile.internal.model.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static com.javarush.jira.login.Role.ADMIN;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private ProfileMapper profileMapper;

    @MockBean
    private ErrorMessageHandler errorMessageHandler;

    @MockBean
    private RestTemplateBuilder restTemplateBuilder;

    @MockBean
    private RestTemplate restTemplate;

    private Profile profile;
    private ProfileTo profileTo;

    @BeforeEach
    void setUp() {
        User user = new User(
            1L, // id
            "test@example.com", // email
            "password123", // password
            "John", // firstName
            "Doe", // lastName
            null, // displayName
            ADMIN // roles
    );
        restTemplate = new RestTemplate();
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        AuthUser authUser = new AuthUser(user);
        when(authentication.getPrincipal()).thenReturn(authUser);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        profile = new Profile();
        profile.setId(1L);
        profile.setLastLogin(LocalDateTime.now());
        profile.setMailNotifications(1L);

        profileTo = new ProfileTo(1L, Collections.singleton("NEWS"), Collections.emptySet());
        profileTo.setLastLogin(profile.getLastLogin());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void get_success() throws Exception {
        when(profileRepository.findById(anyLong())).thenReturn(Optional.of(profile));
        when(profileMapper.toTo(any())).thenReturn(profileTo);

        mockMvc.perform(get(ProfileRestController.REST_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastLogin").value(profile.getLastLogin().toString()))
                .andExpect(jsonPath("$.mailNotifications").value("NEWS"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void get_notFound() throws Exception {
        when(profileRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get(ProfileRestController.REST_URL))
                .andExpect(content().string(""))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.lastLogin").doesNotExist())
                .andExpect(jsonPath("$.mailNotifications").doesNotExist());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    void update_success() throws Exception {
        when(profileMapper.updateFromTo(any(), any())).thenReturn(profile);

        try {
            profileRepository.save(profile);
        } catch (Exception e) {
            fail("No exception expected");
        }

        mockMvc.perform(put(ProfileRestController.REST_URL)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())  // Expect HTTP 200
                .andExpect(jsonPath("$.id").value(profile.getId()))
                .andExpect(jsonPath("$.lastLogin").value(profile.getLastLogin().toString()))
                .andExpect(jsonPath("$.mailNotifications").value("NEWS"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void update_failed() throws Exception {
        when(profileMapper.updateFromTo(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> ValidationUtil.assureIdConsistent(profileTo, -1))
                .isInstanceOf(IllegalRequestDataException.class);
    }
}




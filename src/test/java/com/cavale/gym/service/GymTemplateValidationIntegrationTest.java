package com.cavale.gym.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.cavale.TestcontainersConfiguration;
import com.cavale.gym.domain.GymTemplate;
import com.cavale.gym.dto.TemplateDtos.TemplateRequest;
import com.cavale.gym.dto.TemplateDtos.VariantRequest;
import com.cavale.user.domain.User;
import com.cavale.user.repository.UserRepository;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The MCP front door builds request records itself and calls the services
 * directly, so it never went through the controllers' {@code @Valid}. A note
 * longer than its column then reached Postgres and came back as a raw
 * "value too long for type character varying(300)" SQL error instead of a
 * readable message. The constraints now live on the service boundary, where
 * BOTH front doors meet them.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GymTemplateValidationIntegrationTest {

    @Autowired
    private GymTemplateService gymTemplateService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void addVariant_rejectsAnOversizedNoteInsteadOfLettingItHitTheColumn() {
        User user = activeUser("gym-validation@cavale.run");
        GymTemplate template = gymTemplateService.createTemplate(user.getId(),
                new TemplateRequest("Force hivernale", null, null));

        assertThatThrownBy(() -> gymTemplateService.addVariant(user.getId(), template.getId(),
                new VariantRequest("B", "x".repeat(301))))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Note must not exceed 300 characters");
    }

    @Test
    void addVariant_stillAcceptsANoteThatFitsTheColumn() {
        User user = activeUser("gym-validation-ok@cavale.run");
        GymTemplate template = gymTemplateService.createTemplate(user.getId(),
                new TemplateRequest("Force estivale", null, null));

        assertThatCode(() -> gymTemplateService.addVariant(user.getId(), template.getId(),
                new VariantRequest("B", "x".repeat(300)))).doesNotThrowAnyException();
    }

    @Test
    void createTemplate_rejectsABlankName() {
        User user = activeUser("gym-validation-blank@cavale.run");

        assertThatThrownBy(() -> gymTemplateService.createTemplate(user.getId(),
                new TemplateRequest("  ", null, null)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Name is required");
    }

    private User activeUser(String email) {
        User user = new User(email, "{noop}s3cret-pass", "Validation");
        user.activate();
        User saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();
        return saved;
    }
}

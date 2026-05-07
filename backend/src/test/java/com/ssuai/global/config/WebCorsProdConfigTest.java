package com.ssuai.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebCorsProdConfigTest {

    @Test
    void beanLoadsUnderProdProfileWhenFrontendOriginIsSet() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(WebCorsProdConfig.class)
                .withPropertyValues("ssuai.frontend.origin=https://test.example.com")
                .run(ctx -> assertThat(ctx).hasSingleBean(WebCorsProdConfig.class));
    }

    @Test
    void beanIsAbsentUnderDevProfile() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .withUserConfiguration(WebCorsProdConfig.class)
                .withPropertyValues("ssuai.frontend.origin=https://test.example.com")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(WebCorsProdConfig.class));
    }

    @Test
    void beanIsAbsentUnderTestProfile() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test"))
                .withUserConfiguration(WebCorsProdConfig.class)
                .withPropertyValues("ssuai.frontend.origin=https://test.example.com")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(WebCorsProdConfig.class));
    }

    @Test
    void contextFailsUnderProdWhenFrontendOriginIsMissing() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(WebCorsProdConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ssuai.frontend.origin");
                });
    }

    @Test
    void contextFailsUnderProdWhenFrontendOriginIsBlank() {
        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withUserConfiguration(WebCorsProdConfig.class)
                .withPropertyValues("ssuai.frontend.origin=   ")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
